package com.studybuddy.group.controller;

import com.studybuddy.group.dto.*;
import com.studybuddy.group.model.GroupJoinRequest;
import com.studybuddy.group.service.StudyGroupService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/groups")
public class StudyGroupController {
    private final StudyGroupService service;
    public StudyGroupController(StudyGroupService service) { this.service=service; }
    private String actor(Jwt jwt) { return UUID.fromString(jwt.getClaimAsString("uid")).toString(); }
    @GetMapping public List<GroupView> all(@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue="discover") String scope) { return service.findAll(actor(jwt),scope); }
    @GetMapping("/overview") public GroupOverview overview(@AuthenticationPrincipal Jwt jwt) { return service.overview(actor(jwt)); }
    @GetMapping("/{id}") public GroupView one(@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt) { return service.findById(id.toString(),actor(jwt)); }
    @PostMapping public ResponseEntity<GroupView> create(@RequestBody @Valid GroupInput input,@AuthenticationPrincipal Jwt jwt) {
        String id=service.create(input,actor(jwt)); return ResponseEntity.status(HttpStatus.CREATED).body(service.findById(id,actor(jwt)));
    }
    @PutMapping("/{id}") public GroupView update(@PathVariable UUID id,@RequestBody @Valid GroupInput input,@AuthenticationPrincipal Jwt jwt) {
        service.update(id.toString(),actor(jwt),input); return service.findById(id.toString(),actor(jwt));
    }
    @PostMapping("/{id}/close") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void close(@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt) { service.close(id.toString(),actor(jwt)); }
    @DeleteMapping("/{id}/members/{memberId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable UUID id,@PathVariable UUID memberId,@AuthenticationPrincipal Jwt jwt) { service.removeMember(id.toString(),actor(jwt),memberId.toString()); }
    @PostMapping("/{id}/join-requests") @ResponseStatus(HttpStatus.CREATED)
    public GroupJoinRequest join(@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt) { return service.requestToJoin(id.toString(),actor(jwt)); }
    @GetMapping("/{id}/join-requests") public List<JoinRequestView> requests(@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt) { return service.findJoinRequests(id.toString(),actor(jwt)); }
    @GetMapping("/join-requests/mine") public List<JoinRequestView> mine(@AuthenticationPrincipal Jwt jwt) { return service.myRequests(actor(jwt)); }
    @PostMapping("/join-requests/{id}/accept") public GroupJoinRequest accept(@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt) { return service.decide(id.toString(),actor(jwt),true); }
    @PostMapping("/join-requests/{id}/reject") public GroupJoinRequest reject(@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt) { return service.decide(id.toString(),actor(jwt),false); }
}
