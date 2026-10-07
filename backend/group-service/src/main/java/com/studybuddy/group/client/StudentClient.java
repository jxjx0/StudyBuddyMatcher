package com.studybuddy.group.client;

import com.studybuddy.group.dto.*;
import com.studybuddy.group.service.GroupException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import java.util.*;
import java.util.function.Supplier;

@Component
public class StudentClient {
    private final RestClient client;
    public StudentClient(RestClient.Builder builder, @Value("${services.user-service-url}") String url) {
        var factory=new SimpleClientHttpRequestFactory(); factory.setConnectTimeout(3000); factory.setReadTimeout(5000);
        client=builder.baseUrl(url).requestFactory(factory).build();
    }
    public StudentDto findStudent(String id) {
        return call(() -> client.get().uri("/api/students/{id}",id).retrieve().body(StudentDto.class));
    }
    public CourseDto findCourse(String code) {
        return call(() -> client.get().uri("/api/courses/{code}",code).retrieve().body(CourseDto.class));
    }
    public Map<String,StudentDto> students() {
        StudentDto[] students=call(() -> client.get().uri("/api/students").retrieve().body(StudentDto[].class));
        if(students==null) throw unavailable();
        Map<String,StudentDto> result=new HashMap<>();
        for(var student:students) result.put(student.getUserId(),student);
        return result;
    }
    private <T> T call(Supplier<T> request) {
        try { return request.get(); }
        catch (HttpClientErrorException.NotFound e) { return null; }
        catch (RestClientException e) { throw unavailable(); }
    }
    private GroupException unavailable() { return new GroupException(HttpStatus.SERVICE_UNAVAILABLE,"Student service is unavailable. Please try again shortly."); }
}
