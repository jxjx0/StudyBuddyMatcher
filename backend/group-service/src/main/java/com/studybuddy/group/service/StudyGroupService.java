package com.studybuddy.group.service;

import com.studybuddy.group.client.StudentClient;
import com.studybuddy.group.dto.*;
import com.studybuddy.group.model.*;
import com.studybuddy.group.repository.GroupJoinRequestRepository;
import com.studybuddy.group.repository.StudyGroupRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class StudyGroupService {
  private final StudyGroupRepository groupRepository;
  private final GroupJoinRequestRepository requestRepository;
  private final StudentClient studentClient;
  private final TransactionTemplate transaction;

  public StudyGroupService(
      StudyGroupRepository groupRepository,
      GroupJoinRequestRepository requestRepository,
      StudentClient studentClient,
      PlatformTransactionManager transactionManager) {
    this.groupRepository = groupRepository;
    this.requestRepository = requestRepository;
    this.studentClient = studentClient;
    this.transaction = new TransactionTemplate(transactionManager);
  }

  public GroupOverview overview(String currentStudentId) {
    // Independent reads run together. Closing the executor waits for both database reads.
    try (var reads = Executors.newVirtualThreadPerTaskExecutor()) {
      var groupsTask = reads.submit(groupRepository::findAll);
      var requestsTask = reads.submit(() -> requestRepository.findByStudentId(currentStudentId));
      Map<String, StudentDto> studentsById = studentClient.students();
      requireActiveStudent(studentsById.get(currentStudentId));
      return buildOverview(currentStudentId, studentsById, requestsTask.get(), groupsTask.get());
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new GroupException(
          HttpStatus.SERVICE_UNAVAILABLE, "Group loading was interrupted. Please try again.");
    } catch (ExecutionException exception) {
      // Keep the same error response when a database read fails on another thread.
      if (exception.getCause() instanceof RuntimeException cause) {
        throw cause;
      }
      throw new GroupException(
          HttpStatus.SERVICE_UNAVAILABLE, "Groups are unavailable. Please try again shortly.");
    }
  }

  private GroupOverview buildOverview(
      String currentStudentId,
      Map<String, StudentDto> studentsById,
      List<GroupJoinRequest> ownRequests,
      List<StudyGroup> savedGroups) {
    List<GroupView> discoverGroups = new ArrayList<>();
    List<GroupView> memberGroups = new ArrayList<>();
    Map<String, String> groupNames = new HashMap<>();

    for (StudyGroup group : savedGroups) {
      groupNames.put(group.getGroupId(), group.getGroupName());
      boolean visibleToEveryone = isDiscoverable(group, studentsById);
      boolean isMember = group.hasMember(currentStudentId);
      if (visibleToEveryone || isMember) {
        GroupView groupView = toGroupView(group, currentStudentId, studentsById, ownRequests);
        if (visibleToEveryone) {
          discoverGroups.add(groupView);
        }
        if (isMember) {
          memberGroups.add(groupView);
        }
      }
    }

    List<JoinRequestView> requestHistory = new ArrayList<>();
    for (GroupJoinRequest request : ownRequests) {
      String groupName = groupNames.get(request.getGroupId());
      requestHistory.add(toRequestView(request, groupName, studentsById));
    }
    return new GroupOverview(discoverGroups, memberGroups, requestHistory);
  }

  public List<GroupView> findAll(String currentStudentId, String scope) {
    if (!Set.of("discover", "mine").contains(scope)) {
      throw new IllegalArgumentException("Scope must be discover or mine");
    }
    GroupOverview overview = overview(currentStudentId);
    if (scope.equals("mine")) {
      return overview.mine();
    }
    return overview.groups();
  }

  public GroupView findById(String groupId, String currentStudentId) {
    return details(groupId, currentStudentId).group();
  }

  public GroupDetails details(String groupId, String currentStudentId) {
    // Independent reads overlap; no account status or permissions are cached across requests.
    try (var reads = Executors.newVirtualThreadPerTaskExecutor()) {
      var groupTask = reads.submit(() -> requireGroup(groupId, false));
      var requestsTask = reads.submit(() -> requestRepository.findByGroupId(groupId));
      Map<String, StudentDto> studentsById = studentClient.students();
      requireActiveStudent(studentsById.get(currentStudentId));
      StudyGroup group = groupTask.get();
      List<GroupJoinRequest> groupRequests = requestsTask.get();
      List<GroupJoinRequest> ownRequests = new ArrayList<>();
      for (GroupJoinRequest request : groupRequests) {
        if (request.getStudentId().equals(currentStudentId)) ownRequests.add(request);
      }
      if (!isDiscoverable(group, studentsById)
          && !group.hasMember(currentStudentId)
          && ownRequests.isEmpty()) {
        throw new GroupException(HttpStatus.NOT_FOUND, "Group not found");
      }
      List<JoinRequestView> leaderRequests = new ArrayList<>();
      if (group.isLedBy(currentStudentId)) {
        for (GroupJoinRequest request : groupRequests) {
          leaderRequests.add(toRequestView(request, group.getGroupName(), studentsById));
        }
      }
      return new GroupDetails(
          toGroupView(group, currentStudentId, studentsById, ownRequests),
          List.copyOf(leaderRequests));
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new GroupException(
          HttpStatus.SERVICE_UNAVAILABLE, "Group loading was interrupted. Please try again.");
    } catch (ExecutionException exception) {
      if (exception.getCause() instanceof RuntimeException cause) throw cause;
      throw new GroupException(
          HttpStatus.SERVICE_UNAVAILABLE, "Groups are unavailable. Please try again shortly.");
    }
  }

  public String create(GroupInput input, String currentStudentId) {
    requireActiveStudent(studentClient.findStudent(currentStudentId));
    if (input.courseCode() == null || input.courseCode().isBlank()) {
      throw new IllegalArgumentException("Choose a course");
    }
    if (studentClient.findCourse(input.courseCode().trim()) == null) {
      throw new GroupException(HttpStatus.NOT_FOUND, "Course not found");
    }
    StudyGroup group =
        StudyGroup.create(
            input.groupName(),
            input.description(),
            input.courseCode(),
            currentStudentId,
            input.capacity(),
            input.meetingMode(),
            input.studyGoals(),
            input.availability());
    return transaction.execute(transactionStatus -> groupRepository.save(group).getGroupId());
  }

  public void update(String groupId, String currentStudentId, GroupInput input) {
    changeGroupAsLeader(
        groupId,
        currentStudentId,
        group -> {
          if (input.courseCode() != null && !group.getCourseCode().equals(input.courseCode())) {
            throw new IllegalArgumentException("A group's course cannot be changed");
          }
          group.updateDetails(
              input.groupName(),
              input.description(),
              input.capacity(),
              input.meetingMode(),
              input.studyGoals(),
              input.availability());
        });
  }

  public void close(String groupId, String currentStudentId) {
    changeGroupAsLeader(
        groupId,
        currentStudentId,
        group -> {
          group.close();
          for (GroupJoinRequest request : requestRepository.findByGroupId(groupId)) {
            if (request.isPending()) {
              request.reject();
              requestRepository.save(request);
            }
          }
        });
  }

  public void removeMember(String groupId, String currentStudentId, String memberId) {
    changeGroupAsLeader(groupId, currentStudentId, group -> group.removeMember(memberId));
  }

  private void changeGroupAsLeader(
      String groupId, String currentStudentId, Consumer<StudyGroup> change) {
    requireActiveStudent(studentClient.findStudent(currentStudentId));
    transaction.executeWithoutResult(
        transactionStatus -> {
          StudyGroup group = requireGroup(groupId, true);
          requireLeader(group, currentStudentId);
          change.accept(group);
          groupRepository.save(group);
        });
  }

  public GroupJoinRequest requestToJoin(String groupId, String currentStudentId) {
    // One fresh directory read validates both accounts, before holding any database locks.
    Map<String, StudentDto> studentsById = studentClient.students();
    requireActiveStudent(studentsById.get(currentStudentId));

    return transaction.execute(
        transactionStatus -> {
          StudyGroup group = requireGroup(groupId, true);
          requireActiveStudent(studentsById.get(group.getLeaderId()));
          group.requireActive();
          if (group.hasMember(currentStudentId)) {
            throw new IllegalStateException("You are already a member");
          }
          if (group.isFull()) {
            throw new IllegalStateException("This group is full");
          }
          GroupJoinRequest request = GroupJoinRequest.create(currentStudentId, groupId);
          if (!requestRepository.insertPending(request)) {
            throw new IllegalStateException("You already have a pending request");
          }
          return request;
        });
  }

  public GroupJoinRequest cancelJoinRequest(String groupId, String currentStudentId) {
    return transaction.execute(
        transactionStatus -> {
          // Keep the same group-before-request lock order as approval and closing.
          if (!groupRepository.lockExistsById(groupId)) {
            throw new GroupException(HttpStatus.NOT_FOUND, "Group not found");
          }
          GroupJoinRequest pendingRequest =
              requestRepository
                  .lockPendingByGroupAndStudent(groupId, currentStudentId)
                  .orElseThrow(
                      () ->
                          new GroupException(
                              HttpStatus.NOT_FOUND, "Pending join request not found"));
          if (!pendingRequest.getStudentId().equals(currentStudentId)) {
            throw new GroupException(
                HttpStatus.FORBIDDEN, "You can only cancel your own join request");
          }
          if (!pendingRequest.isPending()) {
            throw new IllegalStateException("This join request is no longer pending");
          }
          pendingRequest.cancel();
          return requestRepository.save(pendingRequest);
        });
  }

  public GroupJoinRequest decide(String requestId, String currentStudentId, boolean accept) {
    requireActiveStudent(studentClient.findStudent(currentStudentId));
    GroupJoinRequest requestBeforeLock = requireRequest(requestId, false);
    StudyGroup groupBeforeLock = requireGroup(requestBeforeLock.getGroupId(), false);
    requireLeader(groupBeforeLock, currentStudentId);
    if (accept) {
      requireActiveStudent(studentClient.findStudent(requestBeforeLock.getStudentId()));
    }

    return transaction.execute(
        transactionStatus -> {
          // Always lock the group before the request. Do not make HTTP calls while holding locks.
          StudyGroup group = requireGroup(requestBeforeLock.getGroupId(), true);
          requireLeader(group, currentStudentId);
          group.requireActive();
          GroupJoinRequest request = requireRequest(requestId, true);
          if (accept) {
            request.accept();
            group.addMember(request.getStudentId());
            groupRepository.save(group);
          } else {
            request.reject();
          }
          return requestRepository.save(request);
        });
  }

  public List<JoinRequestView> findJoinRequests(String groupId, String currentStudentId) {
    GroupDetails details = details(groupId, currentStudentId);
    if (!details.group().isLeader()) {
      throw new GroupException(HttpStatus.FORBIDDEN, "Only the group leader can do this");
    }
    return details.requests();
  }

  public List<JoinRequestView> myRequests(String currentStudentId) {
    return overview(currentStudentId).requests();
  }

  private JoinRequestView toRequestView(
      GroupJoinRequest request, String groupName, Map<String, StudentDto> studentsById) {
    StudentDto student = studentsById.get(request.getStudentId());
    String studentName = student == null ? "Unavailable student" : student.getName();
    return new JoinRequestView(
        request.getGroupJoinRequestId(),
        request.getStudentId(),
        studentName,
        request.getGroupId(),
        groupName,
        request.getStatus(),
        request.getCreatedAt(),
        request.getRespondedAt());
  }

  private GroupView toGroupView(
      StudyGroup group,
      String currentStudentId,
      Map<String, StudentDto> studentsById,
      List<GroupJoinRequest> ownRequests) {
    GroupJoinRequestStatus requestStatus = latestRequestStatus(group.getGroupId(), ownRequests);
    boolean leaderAvailable = isActiveStudent(studentsById.get(group.getLeaderId()));
    boolean isMember = group.hasMember(currentStudentId);
    boolean canRequest =
        group.isActive()
            && leaderAvailable
            && !group.isFull()
            && !isMember
            && requestStatus != GroupJoinRequestStatus.PENDING;

    List<GroupView.Member> members = new ArrayList<>();
    for (String memberId : group.getMemberIds()) {
      StudentDto student = studentsById.get(memberId);
      String memberName = student == null ? "Unavailable student" : student.getName();
      members.add(new GroupView.Member(memberId, memberName, isActiveStudent(student)));
    }
    return new GroupView(
        group.getGroupId(),
        group.getGroupName(),
        group.getDescription(),
        group.getCourseCode(),
        group.getStudyGoals(),
        group.getMeetingMode(),
        group.getAvailability(),
        group.getMaxGroupSize(),
        members.size(),
        group.getStatus(),
        group.getLeaderId(),
        List.copyOf(members),
        group.isLedBy(currentStudentId),
        isMember,
        leaderAvailable,
        canRequest,
        requestStatus);
  }

  private GroupJoinRequestStatus latestRequestStatus(
      String groupId, List<GroupJoinRequest> ownRequests) {
    // The repository returns the newest request first, including accepted and rejected history.
    for (GroupJoinRequest request : ownRequests) {
      if (request.getGroupId().equals(groupId)) {
        return request.getStatus();
      }
    }
    return null;
  }

  private boolean isDiscoverable(StudyGroup group, Map<String, StudentDto> studentsById) {
    return group.isActive() && isActiveStudent(studentsById.get(group.getLeaderId()));
  }

  private StudyGroup requireGroup(String groupId, boolean lockForUpdate) {
    Optional<StudyGroup> savedGroup;
    if (lockForUpdate) {
      savedGroup = groupRepository.lockById(groupId);
    } else {
      savedGroup = groupRepository.findById(groupId);
    }
    return savedGroup.orElseThrow(
        () -> new GroupException(HttpStatus.NOT_FOUND, "Group not found"));
  }

  private GroupJoinRequest requireRequest(String requestId, boolean lockForUpdate) {
    Optional<GroupJoinRequest> savedRequest;
    if (lockForUpdate) {
      savedRequest = requestRepository.lockById(requestId);
    } else {
      savedRequest = requestRepository.findById(requestId);
    }
    return savedRequest.orElseThrow(
        () -> new GroupException(HttpStatus.NOT_FOUND, "Request not found"));
  }

  private void requireLeader(StudyGroup group, String currentStudentId) {
    if (!group.isLedBy(currentStudentId)) {
      throw new GroupException(HttpStatus.FORBIDDEN, "Only the group leader can do this");
    }
  }

  private boolean isActiveStudent(StudentDto student) {
    return student != null && student.getAccountStatus() == AccountStatus.ACTIVE;
  }

  private void requireActiveStudent(StudentDto student) {
    if (!isActiveStudent(student)) {
      throw new GroupException(HttpStatus.FORBIDDEN, "An active student profile is required");
    }
  }
}
