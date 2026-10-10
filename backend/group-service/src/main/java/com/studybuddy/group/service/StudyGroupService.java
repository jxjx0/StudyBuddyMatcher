package com.studybuddy.group.service;

import com.studybuddy.group.client.StudentClient;
import com.studybuddy.group.dto.*;
import com.studybuddy.group.model.*;
import com.studybuddy.group.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.function.Consumer;

@Service
public class StudyGroupService {
    private final StudyGroupRepository groups;
    private final GroupJoinRequestRepository requests;
    private final StudentClient students;
    private final TransactionTemplate transaction;
    public StudyGroupService(StudyGroupRepository groups, GroupJoinRequestRepository requests, StudentClient students, PlatformTransactionManager manager) {
        this.groups=groups; this.requests=requests; this.students=students; transaction=new TransactionTemplate(manager);
    }
    public GroupOverview overview(String actor) {
        var people = students.students();
        requireActive(people.get(actor));
        var ownRequests = requests.findByStudentId(actor);
        var savedGroups = groups.findAll();
        List<GroupView> discover = new ArrayList<>();
        List<GroupView> mine = new ArrayList<>();
        Map<String, String> names = new HashMap<>();

        for (var group : savedGroups) {
            names.put(group.getGroupId(), group.getGroupName());
            boolean visible = group.isActive() && active(people.get(group.getLeaderId()));
            boolean member = group.hasMember(actor);
            if (visible || member) {
                var details = view(group, actor, people, ownRequests);
                if (visible) discover.add(details);
                if (member) mine.add(details);
            }
        }

        List<JoinRequestView> history = new ArrayList<>();
        for (var request : ownRequests) {
            history.add(requestView(request, names.get(request.getGroupId()), people));
        }
        return new GroupOverview(discover, mine, history);
    }
    public List<GroupView> findAll(String actor, String scope) {
        if (!Set.of("discover","mine").contains(scope)) throw new IllegalArgumentException("Scope must be discover or mine");
        var overview = overview(actor);
        return scope.equals("mine") ? overview.mine() : overview.groups();
    }
    public GroupView findById(String id,String actor) {
        var people=students.students(); requireActive(people.get(actor));
        var g=group(id,false); var mine=requests.findByStudentId(actor);
        if ((!g.isActive() || !active(people.get(g.getLeaderId()))) && !g.hasMember(actor) && mine.stream().noneMatch(r->r.getGroupId().equals(id)))
            throw new GroupException(HttpStatus.NOT_FOUND,"Group not found");
        return view(g,actor,people,mine);
    }
    public String create(GroupInput input,String actor) {
        requireActive(students.findStudent(actor));
        if(input.courseCode()==null || input.courseCode().isBlank()) throw new IllegalArgumentException("Choose a course");
        if(students.findCourse(input.courseCode().trim())==null) throw new GroupException(HttpStatus.NOT_FOUND,"Course not found");
        var g=StudyGroup.create(input.groupName(),input.description(),input.courseCode(),actor,input.capacity(),input.meetingMode(),input.studyGoals(),input.availability());
        return transaction.execute(s -> groups.save(g).getGroupId());
    }
    public void update(String id,String actor,GroupInput input) {
        mutate(id,actor,g -> {
            if(input.courseCode()!=null && !g.getCourseCode().equals(input.courseCode())) throw new IllegalArgumentException("A group's course cannot be changed");
            g.updateDetails(input.groupName(),input.description(),input.capacity(),input.meetingMode(),input.studyGoals(),input.availability());
        });
    }
    public void close(String id,String actor) {
        mutate(id,actor,g -> {
            g.close();
            for(var r:requests.findByGroupId(id)) if(r.isPending()) { r.reject(); requests.save(r); }
        });
    }
    public void removeMember(String id,String actor,String member) { mutate(id,actor,g -> g.removeMember(member)); }
    private void mutate(String id,String actor,Consumer<StudyGroup> action) {
        requireActive(students.findStudent(actor));
        transaction.executeWithoutResult(s -> { var g=group(id,true); requireLeader(g,actor); action.accept(g); groups.save(g); });
    }
    public GroupJoinRequest requestToJoin(String id,String actor) {
        requireActive(students.findStudent(actor));
        var snapshot=group(id,false); requireActive(students.findStudent(snapshot.getLeaderId()));
        return transaction.execute(s -> {
            var g=group(id,true); g.requireActive();
            if(g.hasMember(actor)) throw new IllegalStateException("You are already a member");
            if(g.isFull()) throw new IllegalStateException("This group is full");
            if(requests.findByGroupId(id).stream().anyMatch(r -> r.getStudentId().equals(actor) && r.isPending()))
                throw new IllegalStateException("You already have a pending request");
            return requests.save(GroupJoinRequest.create(actor,id));
        });
    }
    public GroupJoinRequest decide(String id,String actor,boolean accept) {
        requireActive(students.findStudent(actor));
        var snapshot=request(id,false); requireLeader(group(snapshot.getGroupId(),false),actor);
        if(accept) requireActive(students.findStudent(snapshot.getStudentId()));
        return transaction.execute(s -> {
            // Every writer locks group first, request second. No HTTP work while locks are held.
            var g=group(snapshot.getGroupId(),true); requireLeader(g,actor); g.requireActive();
            var r=request(id,true);
            if(accept) { r.accept(); g.addMember(r.getStudentId()); groups.save(g); }
            else r.reject();
            return requests.save(r);
        });
    }
    public List<JoinRequestView> findJoinRequests(String id,String actor) {
        var people=students.students(); requireActive(people.get(actor));
        var g=group(id,false); requireLeader(g,actor);
        return requests.findByGroupId(id).stream().map(r->requestView(r,g.getGroupName(),people)).toList();
    }
    public List<JoinRequestView> myRequests(String actor) {
        return overview(actor).requests();
    }
    private JoinRequestView requestView(GroupJoinRequest r,String name,Map<String,StudentDto> people) {
        var student=people.get(r.getStudentId());
        return new JoinRequestView(r.getGroupJoinRequestId(),r.getStudentId(),student==null?"Unavailable student":student.getName(),r.getGroupId(),name,r.getStatus(),r.getCreatedAt(),r.getRespondedAt());
    }
    private GroupView view(StudyGroup g,String actor,Map<String,StudentDto> people,List<GroupJoinRequest> mine) {
        var status=mine.stream().filter(r->r.getGroupId().equals(g.getGroupId())).map(GroupJoinRequest::getStatus).findFirst().orElse(null);
        boolean leaderAvailable=active(people.get(g.getLeaderId()));
        var members=g.getMemberIds().stream().map(id->{var p=people.get(id); return new GroupView.Member(id,p==null?"Unavailable student":p.getName(),active(p));}).toList();
        return new GroupView(g.getGroupId(),g.getGroupName(),g.getDescription(),g.getCourseCode(),g.getStudyGoals(),g.getMeetingMode(),g.getAvailability(),g.getMaxGroupSize(),members.size(),g.getStatus(),g.getLeaderId(),members,g.isLedBy(actor),g.hasMember(actor),leaderAvailable,
            g.isActive() && leaderAvailable && !g.isFull() && !g.hasMember(actor) && status!=GroupJoinRequestStatus.PENDING,status);
    }
    private StudyGroup group(String id,boolean lock) { return (lock?groups.lockById(id):groups.findById(id)).orElseThrow(()->new GroupException(HttpStatus.NOT_FOUND,"Group not found")); }
    private GroupJoinRequest request(String id,boolean lock) { return (lock?requests.lockById(id):requests.findById(id)).orElseThrow(()->new GroupException(HttpStatus.NOT_FOUND,"Request not found")); }
    private void requireLeader(StudyGroup g,String actor) { if(!g.isLedBy(actor)) throw new GroupException(HttpStatus.FORBIDDEN,"Only the group leader can do this"); }
    private boolean active(StudentDto p) { return p!=null && p.getAccountStatus()==AccountStatus.ACTIVE; }
    private void requireActive(StudentDto p) { if(!active(p)) throw new GroupException(HttpStatus.FORBIDDEN,"An active student profile is required"); }
}
