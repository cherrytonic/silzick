/*
 * (C) Copyright 2017-2020 OpenVidu (https://openvidu.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.openvidu.server.game;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.openvidu.client.internal.ProtocolElements;
import io.openvidu.server.core.Participant;
import io.openvidu.server.rpc.RpcNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GameService {

    static final int GETJOBSSETTING = 0;
    static final int SETJOBSSETTING = 1;
    static final int GETREADYSETTING = 2;
    static final int SETREADYSETTING = 3;
    static final int GAMESTART = 4;
    static final int USESKILL = 5;
    static final int EXCHANGENAME = 6;


    private static final Logger log = LoggerFactory.getLogger(GameService.class);

    static RpcNotificationService rpcNotificationService;

    /**
     * 게임 정보 관리.
     */
    //Tread 관리
    protected ConcurrentHashMap<String, Thread> gameThread = new ConcurrentHashMap<>();
    // 역할 관리 <sessionId, <Participant, Roles>>
    protected static ConcurrentHashMap<String, ArrayList<Roles>> gameRoles = new ConcurrentHashMap<>();
    // 역할 - player 매칭 정보 관리
    protected static ConcurrentHashMap<String, ArrayList<Characters>> roleMatching = new ConcurrentHashMap<>();
    // 미션 대상 관리
    protected static ConcurrentHashMap<String, ArrayList<Participant>> missionCandidates = new ConcurrentHashMap<>();
    // 참가자 목록 관리
    protected static ConcurrentHashMap<String, ArrayList<Participant>> participantsList = new ConcurrentHashMap<>();
    // 살아있는 경찰 수 관리
    protected static ConcurrentHashMap<String, Integer> alivePolices = new ConcurrentHashMap<>();
    // 경찰총장, 노트주인 따로 관리
    protected static ConcurrentHashMap<String, ArrayList<Participant>> kiraAndL = new ConcurrentHashMap<>();
    // 데스노트 적힌사람.
    protected static ConcurrentHashMap<String, ArrayList<Characters>> deathNoteList = new ConcurrentHashMap<>();
    // Ready현황 관리
    protected static ConcurrentHashMap<String, HashMap<String, Boolean>> readySetting = new ConcurrentHashMap<>();

    public void gameNavigator(Participant participant, JsonObject message, Set<Participant> participants,
                              String sessionId, RpcNotificationService notice) {

        rpcNotificationService = notice;
        JsonObject params = new JsonObject();

        // data 파싱해서 다시 JSONOBJECT로 바꾸기.
        String dataString = message.get("data").toString();
        JsonObject data = (JsonObject) JsonParser.parseString(dataString);

        // data에 gameStatus로 게임 상태 분기
        int gameStatus = data.get("gameStatus").getAsInt();

        //타입은 game+gameStatus로 보내준다. 예시 : game2 or game3
        String type = message.get("type").getAsString();
        params.addProperty(ProtocolElements.PARTICIPANTSENDMESSAGE_TYPE_PARAM, type);

        switch (gameStatus) {
            case GETJOBSSETTING: // 사전 게임 정보값 얻기. 0번
                getJobsSetting(participant, sessionId, participants, params, data, notice);
                return;
            case SETJOBSSETTING: // 사전 게임 정보값 세팅하기. 1번
                setJobsSetting(participant, sessionId, participants, params, data, notice);
                return;
            case GETREADYSETTING: // 사전 레디정보 얻기 2번
                getReadySetting(participant, sessionId, participants, params, data, notice);
                return;
            case SETREADYSETTING: // 사전 레디정보 세팅 3번
                setReadySetting(participant, sessionId, participants, params, data, notice);
                return;
            case GAMESTART: // 게임 시작
                gameStart(participant, sessionId, participants, params, data, notice);
                return;
            case USESKILL: // 스킬 사용
                useSkill(participant, sessionId, participants, params, data, notice);
                return;
            case EXCHANGENAME: // 명교 후 류자키에게 결과 전달 메소드.
                exchangeName(participant, sessionId, params, data);
                return;
        }
    }

    /**
     * 받는 signal
     * type : 'game0';
     * data :
     * 0 : {
     * jobName : 직업이름,
     * count : 직업 수
     * },
     * 1 : {
     * ...
     * }
     * <p>
     * 역할 데이터 가져오기
     */
    private void getJobsSetting(Participant participant, String sessionId, Set<Participant> participants, JsonObject params, JsonObject data, RpcNotificationService notice) {
        //살아있는 경찰 초기값이 없으면 넣어주기. 있으면 변동 X
        alivePolices.putIfAbsent(sessionId, 1);
        //게임 롤 아무것도 없으면 일단 빈 배열 넣어준다.
        gameRoles.putIfAbsent(sessionId, new ArrayList<>());


        ArrayList<Roles> players = gameRoles.get(sessionId);
        //세션 역할이 비어있으면 새롭게 만듬. 초기값을 담는다.
        if (players.isEmpty()) {
            //KIRA
            players.add(Roles.KIRA);
            //경찰 총장
            players.add(Roles.L);
            //CRIMINAL
            players.add(Roles.CRIMINAL);
            //POLICE
            players.add(Roles.POLICE);
            //BROADCASTER
            players.add(Roles.BROADCASTER);
            //GUARD
            players.add(Roles.GUARD);
            //세션별로 관리.
            gameRoles.compute(sessionId, (k, v) -> v = players);
        }

        //직업이름이랑, 숫자 data에 담기
        setJobsProperty(params, data, players);

        //요청한 사람만 신호 보내주기.
        notice.sendNotification(participant.getParticipantPrivateId(),
                ProtocolElements.PARTICIPANTSENDMESSAGE_METHOD, params);
    }

    /**
     * 보내는 signal
     * type : 'game1';
     * data :
     * {
     * jobName : 이름
     * count : 숫자
     * }
     * 역할 데이터 세팅하기
     */
    private void setJobsSetting(Participant participant, String sessionId,
                                Set<Participant> participants, JsonObject params, JsonObject data, RpcNotificationService notice) {
        log.info("PrepareGame is called by {}", participant.getParticipantPublicId());

        //세션에서 자원 가져오기(초기값은 getPre할때 이미 설정됨)
        ArrayList<Roles> players = gameRoles.get(sessionId);

        String jobName = data.get("jobName").getAsString();
        Integer count = data.get("count").getAsInt();

        //바꾸는 jobName 찾기
        for (Roles r : players) {
            //maxCount 초과 안했으면 바꾸기.
            if (r.getJobName().equals(jobName) && count <= r.getMaxCount()) {
                //경찰이면 살아있는 경찰 수 바꿔주기.
                if (r.getJobName().equals("POLICE")) {
                    alivePolices.computeIfPresent(sessionId, (k, v) -> v = count);
                }
                //역할 수 바꾸기.
                r.setCount(count);
            }
        }

        //바뀐 역할 정보를 갱신.
        gameRoles.computeIfPresent(sessionId, (k, v) -> v = players);

        setJobsProperty(params, data, players);

        //방 참여자들에게 바뀐 데이터 보내주기.
        for (Participant p : participants) {
            notice.sendNotification(p.getParticipantPrivateId(),
                    ProtocolElements.PARTICIPANTSENDMESSAGE_METHOD, params);
        }
    }

    private void setJobsProperty(JsonObject params, JsonObject data, ArrayList<Roles> players) {
        for (int i = 0; i < players.size(); i++) {
            JsonObject temp = new JsonObject();
            temp.addProperty("jobName", players.get(i).getJobName());
            temp.addProperty("count", players.get(i).getCount());
            data.add(Integer.toString(i), temp);
        }
        params.add("data", data);
    }

    /**
     * 처음 방 접속시 접속인원들의 Ready상태를 알려줌.
     */
    private void getReadySetting(Participant participant, String sessionId, Set<Participant> participants, JsonObject params, JsonObject data, RpcNotificationService notice) {
        //session에서 관리되는게 없으면 빈 배열 삽입
        readySetting.putIfAbsent(sessionId, new HashMap<>());

        //기존에 관리되고 있다면 세션별 관리값을 불러온다.
        HashMap<String, Boolean> readyState = readySetting.get(sessionId);

        //새로운 participant, false 기본값 넣고 바꿔준다.
        readyState.put(participant.getParticipantPublicId(), false);
        readySetting.computeIfPresent(sessionId, (k, v) -> v = readyState);

        // publicId : 레디상태(false/true) 로 보냄.
        for (String publicId : readyState.keySet()) {
            data.addProperty(publicId, readyState.get(publicId));
        }
        params.add("data", data);

        //신호 요청자에게 바뀐 데이터 보내주기.
        notice.sendNotification(participant.getParticipantPrivateId(),
                ProtocolElements.PARTICIPANTSENDMESSAGE_METHOD, params);
    }

    private void setReadySetting(Participant participant, String sessionId, Set<Participant> participants, JsonObject params, JsonObject data, RpcNotificationService notice) {
        //레디 상태 가져오기.
        HashMap<String, Boolean> readyState = readySetting.get(sessionId);
        //레디 값 토글
        readyState.replace(participant.getParticipantPublicId(), !readyState.get(participant));
        //레디값 변경.
        readySetting.computeIfPresent(sessionId, (k, v) -> v = readyState);

        int cnt = 0;
        System.out.println(readyState);
        System.out.println(readyState.keySet().size());
        // publicId : true로 보냄.
        for (String publicId : readyState.keySet()) {
            data.addProperty(publicId, readyState.get(publicId));
            if (readyState.get(publicId)) {
                cnt++;
            }
        }

        if (participants.size() >= 6 && participants.size() == cnt) {
            data.addProperty("readyState", true);
        }

        params.add("data", data);

        //방 참여자들에게 바뀐 데이터 보내주기.
        for (Participant p : participants) {
            notice.sendNotification(p.getParticipantPrivateId(),
                    ProtocolElements.PARTICIPANTSENDMESSAGE_METHOD, params);
        }
    }

    /**
     * 게임 시작 가능 여부 확인은 Front에서!
     * type : 'game';
     * data :
     * {
     * gameStatus : 1,
     * }
     * 게임 시작 메소드
     */
    private void gameStart(Participant participant, String sessionId, Set<Participant> participants,
                           JsonObject params, JsonObject data, RpcNotificationService notice) {

        //참가자 목록 가져와서 shuffle
        ArrayList<Participant> players = new ArrayList<>(participants);
        Collections.shuffle(players);

        //역할 매칭 준비
        ArrayList<Characters> userRoles = new ArrayList<>();

        //역할 준비
        ArrayList<Roles> roles = gameRoles.get(sessionId);

        //역할 분배
        int cnt = 0;
        for (Roles r : roles) {
            for (int i = 0; i < r.getCount(); i++) {
                userRoles.add(new Characters(r, players.get(cnt++)));
            }
        }
        //매칭 정보 세션별로 관리
        roleMatching.computeIfPresent(sessionId, (k, v) -> v = userRoles);

        ArrayList<Participant> KIRAandL = new ArrayList<>(players.subList(0, 2));

        //중요 역할들 목록에 담기
        kiraAndL.putIfAbsent(sessionId, KIRAandL);

        //미션 수행할 사람 목록.
        ArrayList<Participant> mCandidates = new ArrayList<>(players.subList(2, userRoles.size()));

        //키라랑 L빼고 미션 수행 대기자로 등록
        // 미션 수행자 목록 넣어서 관리.
        missionCandidates.putIfAbsent(sessionId, mCandidates);

        //각자에게 역할 알려주기.
        for (int i = 0; i < userRoles.size(); i++) {
            data.addProperty("jobName", userRoles.get(i).getRoles().getJobName());
            params.add("data", data);
            rpcNotificationService.sendNotification(userRoles.get(i).getParticipant().getParticipantPrivateId(),
                    ProtocolElements.PARTICIPANTSENDMESSAGE_METHOD, params);
        }

        //쓰래드 생성 및 등록.
        GameRunnable gameRunnable = new GameRunnable(sessionId, roleMatching.get(sessionId), participantsList.get(sessionId), missionCandidates.get(sessionId), notice);
        Thread deathNoteThread = new Thread(gameRunnable);

        //스래드 시작.(명교, 미션 쓰레드 두개 다 시작)
        deathNoteThread.start();

        //게임 끝날때 삭제 했기 때문에 ifAbsent씀.
        gameThread.putIfAbsent(sessionId, deathNoteThread);

    }

    //스킬 사용 메소드
    private void useSkill(Participant participant, String sessionId, Set<Participant> participants,
                          JsonObject params, JsonObject data, RpcNotificationService notice) {

        /**
         * 데이터 전송 예시
         * type : 'game';
         * data :
         * {
         *   gameStatus : 2,
         *   skillType : kill / protect / announce / note / noteUse
         *   target : connectionId
         *   (kill, note에만 필요) name : 'L', 'KIRA', 'GUARD', 'BROADCASTER', 'CRIMINAL', 'POLICE' 중 하나.
         *   (announce에만 필요) announceMessage : "으아아아아 테스트!!"
         * }
         */
        //사용하는 스킬 타입 구별
        String skillType = data.get("skillType").getAsString();
        //역할 리스트 가져오기.
        ArrayList<Characters> cList = roleMatching.get(sessionId);

        String skillTarget = data.get("target").getAsString();
        Characters target = null;
        String name = null;

        //connectionId로 Character 찾아옴.
        //connectionId 자원관리 필수!!!! 나중에 숫자로 관리 된다면 편하게 구현 가능.
        for (int i = 0; i < cList.size(); i++) {
            if (cList.get(i).getParticipant().getParticipantPublicId().equals(skillTarget)) {
                target = cList.get(i);
            }
        }

        //중요인물 리스트의 0번 = 키라, 1번 = 경찰총장
        Participant KIRA = kiraAndL.get(sessionId).get(0);

        switch (skillType) {
            case "kill":
                name = data.get("name").getAsString();
                //skill대상의 직업이 name과 일치하는지 체크
                if (target.getRoles().toString().equals(name)) {
                    //보호되는 상태가 아니면
                    if (!target.isProtected()) {
                        //사망처리
                        target.setAlive(false);

                        //경찰일시 경찰 수 -1;
                        if (target.getRoles() == Roles.POLICE) {
                            alivePolices.computeIfPresent(sessionId, (k, v) -> v - 1);
                        }

                        //사망 소식 전하기.
                        data = new JsonObject();
                        data.addProperty("dead", target.getParticipant().getParticipantPublicId());
                        params.add("data", data);
                        for (Participant p : participants) {
                            rpcNotificationService.sendNotification(p.getParticipantPrivateId(),
                                    ProtocolElements.PARTICIPANTSENDMESSAGE_METHOD, params);

                        }
                        //보호 중이면.
                    } else {
                        //방어됨 소식 알리기.
                        data = new JsonObject();
                        data.addProperty("isprotected", target.getParticipant().getParticipantPublicId());
                        params.add("data", data);
                        for (Participant p : participants) {
                            rpcNotificationService.sendNotification(p.getParticipantPrivateId(),
                                    ProtocolElements.PARTICIPANTSENDMESSAGE_METHOD, params);
                        }
                    }
                }
                break;
            case "protect":
                //스킬 타겟 보호 설정
                target.setProtected(true);
                break;

//                //announce 필요한가?????
//            case "announce":
//                String announce = data.get("announceMessage").getAsString();
//                for (Participant p : participants) {
//                    rpcNotificationService.sendNotification(p.getParticipantPrivateId(),
//                            ProtocolElements.PARTICIPANTSENDMESSAGE_METHOD, params);
//                }
//                break;
            case "note":
                name = data.get("name").getAsString();
                //노트 목록 불러오기
                ArrayList<Characters> noteList = deathNoteList.get(sessionId);

                //성공시 이름 적은 아이디, 실패시 실패 문구.
                if (target.getRoles().toString().equals(name)) {
                    //노트에 사람 적기
                    noteList.add(target);
                    data.addProperty("writeName", target.getParticipant().getParticipantPublicId());
                    params.add("data", data);
                    //노트 집어넣기. ???이렇게 바뀌면 되나??
                    deathNoteList.computeIfPresent(sessionId, (k, v) -> v = noteList);
                } else {
                    data.addProperty("writeName", "the name isn't matched");
                    params.add("data", data);
                }
                //스킬 사용 결과 키라에게 알리기.
                rpcNotificationService.sendNotification(KIRA.getParticipantPrivateId(),
                        ProtocolElements.PARTICIPANTSENDMESSAGE_METHOD, params);
                break;
            case "noteUse":
                noteList = deathNoteList.get(sessionId);

                // 노트에 적힌 사람들 죄다 죽이기.
                for (Characters c : noteList) {
                    //보호되는 상태가 아니면
                    if (!c.isProtected()) {
                        //사망처리
                        c.setAlive(false);

                        //경찰일시 경찰 수 -1;
                        if (c.getRoles() == Roles.POLICE) {
                            alivePolices.computeIfPresent(sessionId, (k, v) -> v - 1);
                        }

                        //사망 소식 전하기.
                        data = new JsonObject();
                        data.addProperty("dead", c.getParticipant().getParticipantPublicId());
                        params.add("data", data);
                        for (Participant p : participants) {
                            rpcNotificationService.sendNotification(p.getParticipantPrivateId(),
                                    ProtocolElements.PARTICIPANTSENDMESSAGE_METHOD, params);

                        }
                        //보호 중이면.
                    } else {
                        //방어됨 소식 알리기.
                        data = new JsonObject();
                        data.addProperty("isprotected", c.getParticipant().getParticipantPublicId());
                        params.add("data", data);
                        for (Participant p : participants) {
                            rpcNotificationService.sendNotification(p.getParticipantPrivateId(),
                                    ProtocolElements.PARTICIPANTSENDMESSAGE_METHOD, params);
                        }
                    }
                }

                deathNoteList.computeIfPresent(sessionId, (k, v) -> v = new ArrayList<Characters>());
                break;
        }


        //키라 사망 or 경찰 수 0명시 게임 종료
        if ((target.getRoles() == Roles.KIRA && !target.isAlive()) || alivePolices.get(sessionId) < 1) {
            finishGame(participant, sessionId, participants, params, data);
        }
    }

    //명교 결과 보내주기.
    private void exchangeName(Participant participant, String sessionId, JsonObject params, JsonObject data) {

        String name = data.get("name").getAsString();

        //역할 가져오기
        ArrayList<Characters> cList = roleMatching.get(sessionId);
        Characters target = null;
        //역할 리스트에서 신호보낸 Participant찾아내기
        for (Characters c : cList) {
            if (c.getParticipant() == participant) {
                target = c;
                break;
            }
        }

        //data 초기화
        data = new JsonObject();
        //명교때 제출한 이름(직업)과 진짜 이름이 같으면 true 아니면 false
        if (target.getRoles().equals(name)) {
            data.addProperty("result", "true");
        } else {
            data.addProperty("result", "false");
        }

        //params에 data 넣기.
        params.add("data", data);

        //중요인물 리스트의 0번 = 키라, 1번 = 경찰총장
        Participant L = kiraAndL.get(sessionId).get(1);
        //명교 결과 경찰 총장에게 알리기.
        rpcNotificationService.sendNotification(L.getParticipantPrivateId(),
                ProtocolElements.PARTICIPANTSENDMESSAGE_METHOD, params);

    }


    //게임 종료 메소드
    private void finishGame(Participant participant, String sessionId, Set<Participant> participants, JsonObject params, JsonObject data) {

        /**
         * 게임 종료시 전달되는 데이터 예시
         * type : 'game';
         * data :
         * {
         *   gameStatus : 4,
         * }
         */

        log.info("finishGame is called by {}", participant.getParticipantPublicId());

        Thread deathNoteThread = gameThread.get(sessionId);

        //자원 반납
        //쓰래드 자원 반납
        gameThread.remove(sessionId);
        //사용 직업 리스트 자원 반납
        gameRoles.remove(sessionId);
        //유저 직업 매칭 자원 반납
        gameRoles.remove(sessionId);
        //미션 수행자 목록 자원 반납.
        missionCandidates.remove(sessionId);
        //참여자 목록 자원 반납.
        participantsList.remove(sessionId);
        //살아있는 경찰 수 자원 반납.
        alivePolices.remove(sessionId);
        //중요인물 자원 반납
        kiraAndL.remove(sessionId);
        //노트 자원 반납.
        deathNoteList.remove(sessionId);


        if (deathNoteThread != null) {
            deathNoteThread.interrupt();
        }

        data.addProperty("gameStatus", 4);
        params.add("data", data);

        //게임 종료 알리기
        for (Participant p : participants) {
            rpcNotificationService.sendNotification(p.getParticipantPrivateId(),
                    ProtocolElements.PARTICIPANTSENDMESSAGE_METHOD, params);
        }
    }
}
