package com.studybuddy.group.controller;

import com.studybuddy.group.dto.*;
import com.studybuddy.group.model.GroupJoinRequest;
import com.studybuddy.group.service.StudyGroupService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/groups")
public class StudyGroupController {
    private final StudyGroupService groupService;

    public StudyGroupController(StudyGroupService groupService) {
        this.groupService = groupService;
    }

    private String currentStudentId(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("uid")).toString();
    }

    @GetMapping
    public List<GroupView> findGroups(@AuthenticationPrincipal Jwt jwt,
                                      @RequestParam(defaultValue = "discover") String scope) {
        return groupService.findAll(currentStudentId(jwt), scope);
    }

    @GetMapping("/overview")
    public GroupOverview overview(@AuthenticationPrincipal Jwt jwt) {
        return groupService.overview(currentStudentId(jwt));
    }

    @GetMapping("/{id}")
    public GroupView findGroup(@PathVariable("id") UUID groupId, @AuthenticationPrincipal Jwt jwt) {
        return groupService.findById(groupId.toString(), currentStudentId(jwt));
    }

    @PostMapping
    public ResponseEntity<GroupView> create(@RequestBody @Valid GroupInput input,
                                            @AuthenticationPrincipal Jwt jwt) {
        String studentId = currentStudentId(jwt);
        String groupId = groupService.create(input, studentId);
        GroupView createdGroup = groupService.findById(groupId, studentId);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdGroup);
    }

    @PutMapping("/{id}")
    public GroupView update(@PathVariable("id") UUID groupId, @RequestBody @Valid GroupInput input,
                            @AuthenticationPrincipal Jwt jwt) {
        String studentId = currentStudentId(jwt);
        groupService.update(groupId.toString(), studentId, input);
        return groupService.findById(groupId.toString(), studentId);
    }

    @PostMapping("/{id}/close")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void close(@PathVariable("id") UUID groupId, @AuthenticationPrincipal Jwt jwt) {
        groupService.close(groupId.toString(), currentStudentId(jwt));
    }

    @DeleteMapping("/{id}/members/{memberId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@PathVariable("id") UUID groupId, @PathVariable UUID memberId,
                             @AuthenticationPrincipal Jwt jwt) {
        groupService.removeMember(groupId.toString(), currentStudentId(jwt), memberId.toString());
    }

    @PostMapping("/{id}/join-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public GroupJoinRequest requestToJoin(@PathVariable("id") UUID groupId, @AuthenticationPrincipal Jwt jwt) {
        return groupService.requestToJoin(groupId.toString(), currentStudentId(jwt));
    }

    @GetMapping("/{id}/join-requests")
    public List<JoinRequestView> findGroupRequests(@PathVariable("id") UUID groupId,
                                                  @AuthenticationPrincipal Jwt jwt) {
        return groupService.findJoinRequests(groupId.toString(), currentStudentId(jwt));
    }

    @GetMapping("/join-requests/mine")
    public List<JoinRequestView> findMyRequests(@AuthenticationPrincipal Jwt jwt) {
        return groupService.myRequests(currentStudentId(jwt));
    }

    @PostMapping("/join-requests/{id}/accept")
    public GroupJoinRequest acceptRequest(@PathVariable("id") UUID requestId, @AuthenticationPrincipal Jwt jwt) {
        return groupService.decide(requestId.toString(), currentStudentId(jwt), true);
    }

    @PostMapping("/join-requests/{id}/reject")
    public GroupJoinRequest rejectRequest(@PathVariable("id") UUID requestId, @AuthenticationPrincipal Jwt jwt) {
        return groupService.decide(requestId.toString(), currentStudentId(jwt), false);
    }
}
