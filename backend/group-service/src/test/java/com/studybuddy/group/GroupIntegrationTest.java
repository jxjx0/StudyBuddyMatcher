package com.studybuddy.group;

import com.studybuddy.group.client.StudentClient;
import com.studybuddy.group.dto.*;
import com.studybuddy.group.model.*;
import com.studybuddy.group.repository.*;
import com.studybuddy.group.service.*;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.net.*;
import java.net.http.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named="GROUP_TEST_JDBC_URL", matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/studybuddy_test")
class GroupIntegrationTest {
    private static final String SECRET="isolated-test-signing-key-only-at-least-32-bytes";
    private static final String A="11111111-1111-4111-8111-111111111111", B="22222222-2222-4222-8222-222222222222", C="33333333-3333-4333-8333-333333333333";
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",()->System.getenv("GROUP_TEST_JDBC_URL"));
        r.add("spring.datasource.username",()->System.getenv("GROUP_TEST_DB_USER"));
        r.add("spring.datasource.password",()->""); r.add("spring.config.import",()->"");
        r.add("spring.sql.init.mode",()->"never"); r.add("app.auth.jwt-secret",()->SECRET);
    }
    @Autowired StudyGroupService service;
    @Autowired StudyGroupRepository groups;
    @Autowired GroupJoinRequestRepository requests;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    @MockitoBean StudentClient students;
    @LocalServerPort int port;
    private final HttpClient http=HttpClient.newHttpClient();
    @BeforeEach void setup() {
        jdbc.execute("TRUNCATE group_join_requests,study_group_members,study_groups CASCADE");
        Map<String,StudentDto> people=new HashMap<>();
        for(String id:List.of(A,B,C)) { var s=new StudentDto();s.setUserId(id);s.setName(id.equals(A)?"Alice":id.equals(B)?"Bob":"Carol");s.setAccountStatus(AccountStatus.ACTIVE);people.put(id,s);when(students.findStudent(id)).thenReturn(s); }
        when(students.students()).thenReturn(people); when(students.findCourse("IS442")).thenReturn(new CourseDto());
    }
    private GroupInput input(int size) { return new GroupInput("OOP revision","Practice together","IS442",List.of(StudyGoal.EXAM_PREPARATION),MeetingMode.ONLINE,List.of(),size); }
    private String create(int size) { return service.create(input(size),A); }
    @Test void newTablesHaveRlsAndNoBrowserRoleGrants() {
        assertEquals(3,jdbc.queryForObject("SELECT count(*) FROM pg_class WHERE relname IN ('study_groups','study_group_members','group_join_requests') AND relrowsecurity",Integer.class));
        for(String table:List.of("study_groups","study_group_members","group_join_requests"))
            for(String role:List.of("anon","authenticated"))
                assertFalse(jdbc.queryForObject("SELECT has_table_privilege(?,?,'SELECT,INSERT,UPDATE,DELETE')",Boolean.class,role,table));
    }
    @Test void persistsApprovalRemovalReapplicationAndCloseHistory() {
        String id=create(3); var request=service.requestToJoin(id,B);
        assertThrows(IllegalStateException.class,()->service.requestToJoin(id,B));
        assertThrows(GroupException.class,()->service.decide(request.getGroupJoinRequestId(),C,true));
        service.decide(request.getGroupJoinRequestId(),A,true);
        assertTrue(groups.findById(id).orElseThrow().hasMember(B));
        assertEquals(GroupJoinRequestStatus.ACCEPTED,requests.findById(request.getGroupJoinRequestId()).orElseThrow().getStatus());
        assertThrows(IllegalStateException.class,()->service.decide(request.getGroupJoinRequestId(),A,true));
        service.removeMember(id,A,B); var retry=service.requestToJoin(id,B); assertNotEquals(request.getGroupJoinRequestId(),retry.getGroupJoinRequestId());
        service.close(id,A); service.close(id,A);
        assertEquals(GroupJoinRequestStatus.REJECTED,requests.findById(retry.getGroupJoinRequestId()).orElseThrow().getStatus());
        assertFalse(groups.findById(id).orElseThrow().isActive());
        assertThrows(IllegalStateException.class,()->service.requestToJoin(id,C));
        assertThrows(GroupException.class,()->service.findById(id,C));
        assertEquals(2,service.myRequests(B).size()); assertNotNull(service.findById(id,B));
    }
    @Test void twoConcurrentApprovalsCannotTakeOneRemainingPlace() throws Exception {
        String id=create(2); var b=service.requestToJoin(id,B);var c=service.requestToJoin(id,C);
        var gate=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            List<Future<Boolean>> jobs=new ArrayList<>();
            for(var r:List.of(b,c)) jobs.add(pool.submit(()->{gate.await();try{service.decide(r.getGroupJoinRequestId(),A,true);return true;}catch(IllegalStateException e){return false;}}));
            gate.countDown(); assertEquals(1,(jobs.get(0).get(10,TimeUnit.SECONDS)?1:0)+(jobs.get(1).get(10,TimeUnit.SECONDS)?1:0));
        }
        assertEquals(2,groups.findById(id).orElseThrow().getMemberIds().size());
        assertEquals(1,requests.findByGroupId(id).stream().filter(r->r.getStatus()==GroupJoinRequestStatus.ACCEPTED).count());
        assertEquals(1,requests.findByGroupId(id).stream().filter(GroupJoinRequest::isPending).count());
    }
    @Test void failedRequestWriteRollsBackMembership() {
        String id=create(2);var request=service.requestToJoin(id,B);
        jdbc.execute("ALTER TABLE group_join_requests ADD CONSTRAINT test_reject_accept CHECK(status <> 'ACCEPTED')");
        try {
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->service.decide(request.getGroupJoinRequestId(),A,true));
            assertFalse(groups.findById(id).orElseThrow().hasMember(B));
            assertTrue(requests.findById(request.getGroupJoinRequestId()).orElseThrow().isPending());
        } finally { jdbc.execute("ALTER TABLE group_join_requests DROP CONSTRAINT test_reject_accept"); }
    }
    @Test void partialIndexPreventsDuplicatePendingHistory() {
        String id=create(2);service.requestToJoin(id,B);
        assertThrows(org.springframework.dao.DuplicateKeyException.class,()->new TransactionTemplate(manager).execute(s->requests.save(GroupJoinRequest.create(B,id))));
    }
    @Test void closeRacingApprovalLeavesNoPendingOrInconsistentMember() throws Exception {
        String id=create(3);var request=service.requestToJoin(id,B);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var start=new CountDownLatch(1);
            var close=pool.submit(()->{start.await();service.close(id,A);return true;});
            var accept=pool.submit(()->{start.await();try{service.decide(request.getGroupJoinRequestId(),A,true);}catch(IllegalStateException ignored){}return true;});
            start.countDown();close.get(10,TimeUnit.SECONDS);accept.get(10,TimeUnit.SECONDS);
        }
        var g=groups.findById(id).orElseThrow();var r=requests.findById(request.getGroupJoinRequestId()).orElseThrow();
        assertFalse(g.isActive());assertFalse(r.isPending());assertEquals(r.getStatus()==GroupJoinRequestStatus.ACCEPTED,g.hasMember(B));
    }
    @Test void resizeRacingApprovalCannotLeaveGroupOverCapacity() throws Exception {
        String id=create(3);var b=service.requestToJoin(id,B);service.decide(b.getGroupJoinRequestId(),A,true);var c=service.requestToJoin(id,C);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var start=new CountDownLatch(1);
            var resize=pool.submit(()->{start.await();try{service.update(id,A,input(2));}catch(IllegalStateException ignored){}return true;});
            var accept=pool.submit(()->{start.await();try{service.decide(c.getGroupJoinRequestId(),A,true);}catch(IllegalStateException ignored){}return true;});
            start.countDown();resize.get(10,TimeUnit.SECONDS);accept.get(10,TimeUnit.SECONDS);
        }
        var g=groups.findById(id).orElseThrow();assertTrue(g.getMemberIds().size()<=g.getMaxGroupSize());
        assertEquals(requests.findById(c.getGroupJoinRequestId()).orElseThrow().getStatus()==GroupJoinRequestStatus.ACCEPTED,g.hasMember(C));
    }
    @Test void missingLeaderHidesDiscoveryButPreservesMemberHistory() {
        String id=create(3);var r=service.requestToJoin(id,B);service.decide(r.getGroupJoinRequestId(),A,true);
        var people=new HashMap<>(students.students());people.remove(A);when(students.students()).thenReturn(people);when(students.findStudent(A)).thenReturn(null);
        assertTrue(service.findAll(C,"discover").isEmpty());assertEquals(1,service.findAll(B,"mine").size());
        assertFalse(service.findById(id,B).leaderAvailable());assertThrows(GroupException.class,()->service.requestToJoin(id,C));
    }
    @Test void overviewMatchesTheExistingTabsAndOnlyLoadsStudentsOnce() {
        String open = create(3);
        var accepted = service.requestToJoin(open, B);
        service.decide(accepted.getGroupJoinRequestId(), A, true);
        String closed = create(3);
        service.requestToJoin(closed, B);
        service.close(closed, A);
        String unavailable = service.create(input(3), C);
        service.requestToJoin(unavailable, B);
        var people = new HashMap<>(students.students());
        people.remove(C);
        when(students.students()).thenReturn(people);

        for (String actor : List.of(A, B)) {
            clearInvocations(students);
            var overview = service.overview(actor);
            verify(students, times(1)).students();
            assertEquals(service.findAll(actor, "discover"), overview.groups());
            assertEquals(service.findAll(actor, "mine"), overview.mine());
            assertEquals(service.myRequests(actor), overview.requests());
        }
        var ownerOverview = service.overview(A);
        assertEquals(List.of(open), ownerOverview.groups().stream().map(GroupView::groupId).toList());
        assertEquals(Set.of(open, closed), new HashSet<>(ownerOverview.mine().stream().map(GroupView::groupId).toList()));
        assertTrue(ownerOverview.requests().isEmpty());
        var memberOverview = service.overview(B);
        assertEquals(List.of(open), memberOverview.mine().stream().map(GroupView::groupId).toList());
        assertEquals(3, memberOverview.requests().size());
        for (var request : memberOverview.requests()) {
            assertEquals(B, request.studentId());
            if (request.groupId().equals(closed)) assertEquals(GroupJoinRequestStatus.REJECTED, request.status());
        }
        assertThrows(GroupException.class, () -> service.overview(C));
    }
    @Test void listingGroupsIncludesEveryMemberInTheSameOrderAsIndividualReads() {
        String first = create(3);
        var request = service.requestToJoin(first, B);
        service.decide(request.getGroupJoinRequestId(), A, true);
        String second = service.create(input(3), C);
        var listed = groups.findAll();
        assertEquals(2, listed.size());
        for (var group : listed) {
            assertEquals(groups.findById(group.getGroupId()).orElseThrow().getMemberIds(), group.getMemberIds());
        }
        assertEquals(List.of(C), groups.findById(second).orElseThrow().getMemberIds());
        assertEquals(2, groups.findById(first).orElseThrow().getMemberIds().size());
    }
    @Test void overviewStartsIndependentReadsWithoutWaitingForAnotherReadToFinish() {
        var groupStore = mock(StudyGroupRepository.class);
        var requestStore = mock(GroupJoinRequestRepository.class);
        var studentClient = mock(StudentClient.class);
        var people = students.students();
        var started = new CountDownLatch(3);
        when(groupStore.findAll()).thenAnswer(call -> {
            started.countDown();
            assertTrue(started.await(3, TimeUnit.SECONDS), "All reads should start together");
            return List.of();
        });
        when(requestStore.findByStudentId(A)).thenAnswer(call -> {
            started.countDown();
            assertTrue(started.await(3, TimeUnit.SECONDS), "All reads should start together");
            return List.of();
        });
        when(studentClient.students()).thenAnswer(call -> {
            started.countDown();
            assertTrue(started.await(3, TimeUnit.SECONDS), "All reads should start together");
            return people;
        });
        var parallelService = new StudyGroupService(groupStore, requestStore, studentClient, manager);
        assertEquals(new GroupOverview(List.of(), List.of(), List.of()), parallelService.overview(A));
    }
    @Test void overviewPreservesDatabaseFailuresFromEitherParallelRead() {
        var groupStore = mock(StudyGroupRepository.class);
        var requestStore = mock(GroupJoinRequestRepository.class);
        var parallelService = new StudyGroupService(groupStore, requestStore, students, manager);
        var failure = new org.springframework.dao.DataAccessResourceFailureException("Database offline");
        when(groupStore.findAll()).thenThrow(failure);
        when(requestStore.findByStudentId(A)).thenReturn(List.of());
        assertSame(failure, assertThrows(org.springframework.dao.DataAccessException.class, () -> parallelService.overview(A)));
        reset(groupStore, requestStore);
        when(groupStore.findAll()).thenReturn(List.of());
        when(requestStore.findByStudentId(A)).thenThrow(failure);
        assertSame(failure, assertThrows(org.springframework.dao.DataAccessException.class, () -> parallelService.overview(A)));
    }
    @Test void accountAndReferenceValidationPrecedesWrites() {
        when(students.findCourse("IS442")).thenReturn(null);assertThrows(GroupException.class,()->create(2)); assertTrue(groups.findAll().isEmpty());
        when(students.findCourse("IS442")).thenReturn(new CourseDto());String id=create(2);var r=service.requestToJoin(id,B);
        var inactive=new StudentDto();inactive.setAccountStatus(AccountStatus.SUSPENDED);when(students.findStudent(B)).thenReturn(inactive);
        assertThrows(GroupException.class,()->service.decide(r.getGroupJoinRequestId(),A,true));assertTrue(requests.findById(r.getGroupJoinRequestId()).orElseThrow().isPending());
        when(students.findStudent(A)).thenReturn(null);assertThrows(GroupException.class,()->service.close(id,A));
    }
    @Test void actualBearerTokensEnforceSignatureExpiryIssuerUidAndRole() throws Exception {
        assertEquals(401,get("/api/groups",null).statusCode());
        assertEquals(200,get("/api/groups",token(A,SECRET,"studybuddy-user-service",3600,"ROLE_STUDENT")).statusCode());
        assertEquals(401,get("/api/groups",token(A,SECRET+"wrong","studybuddy-user-service",3600,"ROLE_STUDENT")).statusCode());
        assertEquals(401,get("/api/groups",token(A,SECRET,"wrong-issuer",3600,"ROLE_STUDENT")).statusCode());
        assertEquals(401,get("/api/groups",token(A,SECRET,"studybuddy-user-service",-120,"ROLE_STUDENT")).statusCode());
        assertEquals(401,get("/api/groups",token(null,SECRET,"studybuddy-user-service",3600,"ROLE_STUDENT")).statusCode());
        assertEquals(401,get("/api/groups",token("invalid",SECRET,"studybuddy-user-service",3600,"ROLE_STUDENT")).statusCode());
        assertEquals(403,get("/api/groups",token(A,SECRET,"studybuddy-user-service",3600,"ROLE_ADMIN")).statusCode());
        assertEquals(401,get("/api/groups/overview",null).statusCode());
        assertEquals(403,get("/api/groups/overview",token(A,SECRET,"studybuddy-user-service",3600,"ROLE_ADMIN")).statusCode());
        assertEquals(200,get("/api/groups/overview",token(A,SECRET,"studybuddy-user-service",3600,"ROLE_STUDENT")).statusCode());
    }
    @Test void spoofedLeaderQueryDoesNotGrantOwnershipAndHistoryIsPrivate() throws Exception {
        String id=create(3);service.requestToJoin(id,B);String token=token(C,SECRET,"studybuddy-user-service",3600,"ROLE_STUDENT");
        var req=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/groups/"+id+"/close?leaderId="+A)).header("Authorization","Bearer "+token).POST(HttpRequest.BodyPublishers.noBody()).build();
        assertEquals(403,http.send(req,HttpResponse.BodyHandlers.ofString()).statusCode());
        assertEquals(403,get("/api/groups/"+id+"/join-requests",token).statusCode());
        assertEquals("[]",get("/api/groups/join-requests/mine",token).body());
        assertEquals(400,get("/api/groups/not-a-uuid",token).statusCode());
    }
    private HttpResponse<String> get(String path,String token) throws Exception {
        var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path));if(token!=null)b.header("Authorization","Bearer "+token);
        return http.send(b.GET().build(),HttpResponse.BodyHandlers.ofString());
    }
    private String token(String uid,String key,String issuer,long seconds,String role) {
        var now=Instant.now();var b=JwtClaimsSet.builder().issuer(issuer).subject("email-not-user-id@example.test").issuedAt(now.minusSeconds(300)).expiresAt(now.plusSeconds(seconds)).claim("roles",List.of(role));
        if(uid!=null)b.claim("uid",uid);
        return new NimbusJwtEncoder(new ImmutableSecret<>(key.getBytes(java.nio.charset.StandardCharsets.UTF_8))).encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),b.build())).getTokenValue();
    }
}
