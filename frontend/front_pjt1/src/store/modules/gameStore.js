import axios from 'axios'
import { OpenVidu } from "openvidu-browser";
import { OPENVIDU_SERVER_URL, OPENVIDU_SERVER_SECRET } from '@/config/index.js'
import { jobs } from './gameUtil.js'
import router from '@/router/index.js'

axios.defaults.headers.post["Content-Type"] = "application/json";

const gameStore = {
  namespaced: true,

  state: {
    // customed
    join: false,
    isHost: false,
    nickname: undefined,
    isReady: false,
    activeGameStart: false,
    readyStatus: false,
    
    // Ovenvidu
    OV: undefined,
    OVToken: undefined,
    sessionId: undefined,
    session: undefined,
    publisher: undefined,
    subscribers: [],

    // 명교방
    subOV: undefined,
    subOVToken: undefined,
    subSession: undefined,
    subPublisher: undefined,
    subSubscribers: [],
    receivedCard: '선택 중',


    //game
    isAlive: true,
    jobs: jobs,
    myJob: undefined,

    //chatting
    messages: [],
  },
  
  mutations: {
    GAME_CHECKIN (state) {
      state.join = true
    },
    GAME_CHECKOUT (state) {
      state.join = false
    },
    NICKNAME_UPDATE (state, res) {
        state.nickname = res
    },
    // 메인페이지에서 "방생성" 누르고 들어오면 isHost = true
    IS_HOST (state) {
      state.isHost = true
    },
    SET_PUBLISHER (state, res) {
      state.publisher = res
    },
    SET_OV (state, res) {
      state.OV = res
    },
    SET_SESSIONID (state, sessionId) {
      state.sessionId = sessionId
    },
    SET_SESSION (state, res) {
      state.session = res
    },
    SET_SUBSCRIBERS (state, res) {
      state.subscribers = res
    },
    SET_OVTOKEN (state, res) {
      state.OVToken = res
    },

    // 명교방 state 설정하기
    SET_SUB_PUBLISHER (state, res) {
      state.subPublisher = res
    },
    SET_SUB_OV (state, res) {
      state.subOV = res
    },
    SET_SUB_SESSION (state, res) {
      state.subSession = res
    },
    SET_SUB_SUBSCRIBERS (state, res) {
      state.subSubscribers = res
    },
    SET_SUB_OVTOKEN (state, res) {
      state.subOVToken = res
    },
    EXCHANGE_OFF (state) {
      state.subOV = undefined
      state.subPublisher = undefined
      state.subSession = undefined
      state.subSubscribers = []
      state.subOVToken = undefined
      // state.is1on1 = false
    },



    // 채팅 관련 기능
    SET_MESSAGES(state, res) {
      state.messages.push(res.message)
    },

    // 게임 관련 기능
    // 직업 리스트 입력
    GET_JOB_PROPS (state, jobProps) {
      state.jobs = jobProps
    },

    // 직업 정보 내 count 증감
    CHANGE_JOB_COUNT(state, jobProps) {
      state.jobs.forEach(job => {
        if (job.jobName === jobProps.jobName) {
          job.count = jobProps.count
        }
      })
    },

    // 명함교환 시 상대방 확정 카드 자원 관리
    RECEIVE_CARD(state, card) {
      state.receivedCard = card
    }
    
  },

  actions: {
    // Attend에서 참가 누르면 닉네임 받아옴. 닉네임 받아서 조인세션허고 직업 리스트 요청
    nicknameUpdate ({ commit, dispatch }, res) {
      commit('NICKNAME_UPDATE', res.nickname)
      commit('SET_SESSIONID', res.sessionId)
      dispatch('subJoinSession')
      dispatch('joinSession')
    },
    // ★★★★★★★★★★★★★★겁나 중요함★★★★★★★★★★★★★★★★★
    // 오픈바이두 연결하는 세션만드는 함수, 닉네입 입력 후 참가 누르면 동작함
    async joinSession({ commit, dispatch, state }) {
      // --- Get an OpenVidu object ---
      const OV = new OpenVidu();
      // --- Init a session ---
      const session = OV.initSession();

      const subscribers = [];
      // --- Specify the actions when events take place in the session ---
      
      // On every new Stream received...
      // stream = 영상 송출과 관련된 정보들
      // 세션에 publisher를 등록하면 자동으로 streamCreated가 실행되고 다른사람의 subscribers에 내 stream정보를 담는 로직
      session.on("streamCreated", ({ stream }) => {
        const subscriber = session.subscribe(stream);
        subscriber.ready = false
        subscribers.push(subscriber);
      });
      
      // On every Stream destroyed...
      session.on("streamDestroyed", ({ stream }) => {
        const index = subscribers.indexOf(stream.streamManager, 0);
        if (index >= 0) {
          subscribers.splice(index, 1);
        }
      });
      
      // On every asynchronous exception...
      session.on("exception", ({ exception }) => {
        console.warn(exception);
      });

      // session.on의 첫번째 인자 = event(String), 두번째 인자 = 앞의 event를 받아서 실행하는 함수(Function)
      // event.data에 채팅 input에서 받은 내용을 parsing해서 state의 messages에 반영
      session.on("signal:chat", (event)=>{
        let eventData = JSON.parse(event.data);
        let data = new Object()
        data.message = eventData.message;
        commit('SET_MESSAGES', data)
      });

      // 게임 관련 시그널 관리
      session.on("signal:game", (event) => {
        // 게임 접속 시 직업 데이터 현황 받기
        if (event.data.gameStatus === 0){
          for (let i=0; i<6; i++) {
            state.jobs.forEach(job => {
              if (job.jobName == event.data[i].jobName) {
                job.count = event.data[i].count
              }
            })
          }
        // job.count 증감 (방장 권한)
        } else if(event.data.gameStatus === 1){
          let job = event.data
          commit('CHANGE_JOB_COUNT', job)
        // 게임 접속 시 ready 현황 받기
        } else if (event.data.gameStatus === 2){
          state.subscribers.forEach(subscriber => {
            subscriber.ready = event.data[subscriber.stream.connection.connectionId]
          })
        // 다른사람이 레디했을 때 정보 받아서 바꾸기 + 6명 이상 레디하면 게임시작 활성화
        } else if (event.data.gameStatus === 3){
          state.subscribers.forEach(subscriber => {
            subscriber.ready = event.data[subscriber.stream.connection.connectionId]
          })
          state.publisher.ready = event.data[state.publisher.stream.connection.connectionId]
          if (event.data.readyStatus) {
            state.readyStatus = true
          } else {
            state.readyStatus = false
          }
        } else if (event.data.gameStatus === 4) {
          state.myJob= event.data.jobName
          router.push({
            name: 'MainGame'
          })
        } else if (event.data.gameStatus === 5) {
          switch (event.data.skillType) {
            case 'noteWrite':{
              const {writeName} = event.data
              if (writeName) {
                state.messages.push('System : 누군가의 이름이 노트에 적혔습니다.')
              } else {
                state.messages.push('System : 이름과 직업이 일치하지 않습니다.')
              }
              break
            }
            case 'noteUse':{
              const results = event.data
              results.forEach(result => {
                const {isAlive, userId, connectionId} = result
                const { clientData } = JSON.parse(userId)
                if (isAlive) {
                  state.messages.push('System : ' + clientData + '가 보디가드에 의해 보호되었습니다.')
                } else {
                  if (state.session.connection.connectionId == connectionId){
                    console.log('심장마비 발동@@@@@@@@@@@@@@')
                    state.session.unpublish(state.publisher)
                    commit('SET_PUBLISHER', undefined)
                    state.isAlive = false
                  }
                  state.messages.push('System : ' + clientData + '가 심장마비로 사망하였습니다.')
                }
              })
              break
            }
            case 'announceToL':{
              let TF = '거짓'
              if (event.data.result) {
                TF = '진실'
              }
              const {clientData} = JSON.parse(event.data.userId)
              const message = "System : " + clientData + "는" + TF + '인 명함을 냈습니다.'
              state.messages.push(message)
              break
            }
          }
        }
      });
      // 명함교환 방 자동 이동 & 미션 할당
      session.on("signal:autoSystem", (event) => {
        // const action = JSON.parse(event.data).action
        if (event.data.action == "exchangeNameStart") {
          state.session.unpublish(state.publisher)
          commit('SET_PUBLISHER', undefined)
          let subPublisher = OV.initPublisher(undefined, {
            audioSource: undefined, // The source of audio. If undefined default microphone
            videoSource: undefined, // The source of video. If undefined default webcam
            publishAudio: true, // Whether you want to start publishing with your audio unmuted or not
            publishVideo: true, // Whether you want to start publishing with your video enabled or not
            resolution: "480x360", // The resolution of your video
            frameRate: 30, // The frame rate of your video
            insertMode: "APPEND", // How the video is inserted in the target element 'video-container'
            mirror: false, // Whether to mirror your local video or not
          });
          commit('SET_SUB_PUBLISHER', subPublisher)
          state.subSession.publish(state.subPublisher);
          router.push({
            name: 'CardExchange',
          })
        }
        // 두명 중 하나가 퍼블리셔면 언퍼블리시하고 라우터푸시 조인세션?
      })

      // --- Connect to the session with a valid user token ---
      // 'getToken' method is simulating what your server-side should do.
      // 'token' parameter should be retrieved and returned by your own backend
      await dispatch("getToken", state.sessionId).then((token) => {
        session
        .connect(token, { clientData: state.nickname })
        .then(() => {
          // --- Get your own camera stream with the desired properties ---
          let publisher = OV.initPublisher(undefined, {
            audioSource: undefined, // The source of audio. If undefined default microphone
            videoSource: undefined, // The source of video. If undefined default webcam
            publishAudio: true, // Whether you want to start publishing with your audio unmuted or not
            publishVideo: true, // Whether you want to start publishing with your video enabled or not
            resolution: "480x360", // The resolution of your video
            frameRate: 30, // The frame rate of your video
            insertMode: "APPEND", // How the video is inserted in the target element 'video-container'
            mirror: false, // Whether to mirror your local video or not
          });
          publisher.ready = false
          commit('SET_OV', OV)
          commit('SET_PUBLISHER', publisher)
          commit('SET_SESSION', session)
          commit('SET_SUBSCRIBERS', subscribers)
          commit('SET_OVTOKEN', token)

          // --- Publish your stream ---
          session.publish(state.publisher)
          router.push({
            name: 'Attend',
            params: { hostname: state.sessionId}
          })
        })
          .catch((error) => {
            console.log(
              "There was an error connecting to the session:",
              error.code,
              error.message
            );
          });
      });
      window.addEventListener("beforeunload", this.leaveSession);
    },
    getToken({ dispatch }, mySessionId) {
      return dispatch('createSession', mySessionId).then((sessionId) =>
        dispatch('createToken', sessionId)
      );
    },
    createSession(context, sessionId) {
      return new Promise((resolve, reject) => {
        axios
          .post(
            `${OPENVIDU_SERVER_URL}/openvidu/api/sessions`,
            JSON.stringify({
              customSessionId: sessionId,}),
            {
              headers: {
                'Content-Type' : 'application/json'
              },
              auth: {
                username: "OPENVIDUAPP",
                password: "MY_SECRET",
              },
            }
          )
          .then((response) =>response.data)
          .then((data) => resolve(data.id))
          .catch((error) => {
            if (error.response.status === 409) {
              resolve(sessionId);
            } else {
              console.warn(
                `No connection to OpenVidu Server. This may be a certificate error at ${OPENVIDU_SERVER_URL}`
              );
              if (
                window.confirm(
                  `No connection to OpenVidu Server. This may be a certificate error at ${OPENVIDU_SERVER_URL}\n\nClick OK to navigate and accept it. If no certificate warning is shown, then check that your OpenVidu Server is up and running at "${OPENVIDU_SERVER_URL}"`
                )
              ) {
                location.assign(`${OPENVIDU_SERVER_URL}/accept-certificate`);
              }
              reject(error.response);
            }
          });
      });
    },
    createToken(context, sessionId) {
      return new Promise((resolve, reject) => {
        axios
          .post(
            `${OPENVIDU_SERVER_URL}/api/tokens`,JSON.stringify({
              "session": sessionId,}),
            {
              auth: {
                username: "OPENVIDUAPP",
                password: OPENVIDU_SERVER_SECRET,
              },
            }
          )
          .then((response) => response.data)
          .then((data) => resolve(data.token))
          .catch((error) => reject(error.response));
      });
    },
    leaveSession({state, commit}) {
      // --- Leave the session by calling 'disconnect' method over the Session object ---
      if (state.session) state.session.disconnect();

      commit('SET_SESSION', undefined)
      commit('SET_PUBLISHER', undefined)
      commit('SET_OV', undefined)
      commit('SET_OVTOKEN', undefined)
      commit('SET_SUBSCRIBERS', [])
      commit('SET_SESSIONID', undefined)
      commit('SET_NICKNAME', undefined)
      commit('NICKNAME_UPDATE', undefined)

      // window.removeEventListener("beforeunload", this.leaveSession);
    },
    // 명교방 관련 기능
    exchange ({dispatch}) {
      dispatch('subJoinSession')
      router.push({
        name: "CardExchange"
      })
    },
    exchangeOff ({commit, state}) {
      commit('EXCHANGE_OFF')
      state.session.publish(state.publisher)
    },
    subJoinSession({ commit, dispatch, state }) {
      // --- Get an OpenVidu object ---
      const subOV = new OpenVidu();
      // --- Init a session ---
      const subSession = subOV.initSession();
      const subSubscribers = [];
      
      // --- Specify the actions when events take place in the session ---
      
      // On every new Stream received...
      subSession.on("streamCreated", ({ stream }) => {
        const subSubscriber = subSession.subscribe(stream);
        subSubscribers.push(subSubscriber);
      });
      // On every Stream destroyed...
      subSession.on("streamDestroyed", ({ stream }) => {
        const subIndex = subSubscribers.indexOf(stream.streamManager, 0);
        if (subIndex >= 0) {
          subSubscribers.splice(subIndex, 1);
        }
      });
      // On every asynchronous exception...
      subSession.on("exception", ({ exception }) => {
        console.warn(exception);
      });

      subSession.on("signal:exchangeCard", (event) => {
        const receivedCard = JSON.parse(event.data).jobName
        dispatch('receiveCard', receivedCard)
      })
      
      // --- Connect to the session with a valid user token ---
      
      // 'getToken' method is simulating what your server-side should do.
      // 'token' parameter should be retrieved and returned by your own backend
      dispatch("getToken", 'sub' + state.sessionId).then((subToken) => {
        subSession
        .connect(subToken, { clientData: state.nickname })
        .then(() => {
          // --- Get your own camera stream with the desired properties ---
          commit('SET_SUB_OV', subOV)
          commit('SET_SUB_SESSION', subSession)
          commit('SET_SUB_OVTOKEN', subToken)
          commit('SET_SUB_SUBSCRIBERS', subSubscribers)
          })
          .catch((error) => {
            console.log(
              "There was an error connecting to the session:",
              error.code,
              error.message
            );
          });
      });
      // window.addEventListener("beforeunload", this.leaveSession);
    },


    // 채팅 관련 통신
    sendMessage ({ state }, message) {
      state.session.signal({
        type: 'chat',
        data: JSON.stringify({message}),
        to: [],
      })
    },
    setReady ({state}) {
      state.session.signal({
        type: 'game',
        data: {
          gameStatus: 3
        },
        to: [],
      })
    },

    // 게임 기능
    changeJobCount({ state }, jobProps) {
      state.session.signal({
        type: 'game',
        data: jobProps,
        to: [],
      })
    },

    getReadyStatus({state}) {
      state.session.signal({
        type: 'game',
        data: {
          gameStatus: 2
        },
        to: [],
      })
    },
    getJobsState({state}) {
      state.session.signal({
        type: 'game',
        data: {
          gameStatus: 0
        },
        to: [],
      })
    },
    exitCard({state, commit}) {
      state.subSession.unpublish(state.subPublisher)
      commit('SET_SUB_PUBLISHER', undefined)
      let publisher = state.OV.initPublisher(undefined, {
        audioSource: undefined, // The source of audio. If undefined default microphone
        videoSource: undefined, // The source of video. If undefined default webcam
        publishAudio: true, // Whether you want to start publishing with your audio unmuted or not
        publishVideo: true, // Whether you want to start publishing with your video enabled or not
        resolution: "480x360", // The resolution of your video
        frameRate: 30, // The frame rate of your video
        insertMode: "APPEND", // How the video is inserted in the target element 'video-container'
        mirror: false, // Whether to mirror your local video or not
      });
      if (state.isAlive){
        commit('SET_PUBLISHER', publisher)
        state.session.publish(state.publisher)
      }
      router.push({
        name: 'MainGame'
      })
    },
    receiveCard({commit}, card) {
      commit('RECEIVE_CARD', card)
    }
  },

}

export default gameStore;