package com.studybuddy.group.client;

import com.studybuddy.group.dto.CourseDto;
import com.studybuddy.group.dto.StudentDto;
import com.studybuddy.group.service.GroupException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class StudentClient {
    private final RestClient client;

    public StudentClient(RestClient.Builder builder, @Value("${services.user-service-url}") String serviceUrl) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(3000);
        requestFactory.setReadTimeout(5000);
        client = builder.baseUrl(serviceUrl).requestFactory(requestFactory).build();
    }

    public StudentDto findStudent(String studentId) {
        return call(() -> client.get()
                .uri("/api/students/{id}", studentId)
                .retrieve()
                .body(StudentDto.class));
    }

    public CourseDto findCourse(String courseCode) {
        return call(() -> client.get()
                .uri("/api/courses/{code}", courseCode)
                .retrieve()
                .body(CourseDto.class));
    }

    public Map<String, StudentDto> students() {
        StudentDto[] students = call(() -> client.get()
                .uri("/api/students")
                .retrieve()
                .body(StudentDto[].class));
        if (students == null) {
            throw unavailable();
        }

        Map<String, StudentDto> studentsById = new HashMap<>();
        for (StudentDto student : students) {
            studentsById.put(student.getUserId(), student);
        }
        return studentsById;
    }

    private <T> T call(Supplier<T> request) {
        try {
            return request.get();
        } catch (HttpClientErrorException.NotFound exception) {
            return null;
        } catch (RestClientException exception) {
            throw unavailable();
        }
    }

    private GroupException unavailable() {
        return new GroupException(HttpStatus.SERVICE_UNAVAILABLE,
                "Student service is unavailable. Please try again shortly.");
    }
}
