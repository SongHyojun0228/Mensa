# **🎮 Java_Swing_Mini_Game : SLIME SURVIVAL**
> **자바 스윙을 이용한 미니 게임**
> Java Swing과 Socket 통신을 활용해 제작한 2D 멀티플레이 생존 게임
> 실시간 통신 구조 설계 및 구현 경험
> 클라이언트 측 이벤트 처리 및 UI 표현

<img width="1276" alt="Image" src="https://github.com/user-attachments/assets/f17275d9-c623-47a2-86c5-656d2f187f68" />


<br><br>


## 🗳️ 선정이유
- **Java를 처음 배우면서 재밌게 배워보자는 생각**으로 게임을 만들어보기로 했습니다.
- **Java Swing으로 화면을 만들고, Socket 통신으로 여러 명이서 플레이할 수 있도록 만들자는 목표**를 잡았습니다.


<br><br>


## 🤖 주요 기능
- **Java Swing 기반의 GUI 게임 구현**
- 슬라임 몬스터 공격 및 생존
- 기본 공격(Z key), 스킬(X key)
- 스킬 이펙트, 사망 애니메이션, 점수 시스템 구현
- **서버-클라이언트 멀티플레이 기능**
    - Socket, ObjectStream 기반 실시간 통신
    - 플레이어 입력 → 서버 처리 → 모든 클라이언트에 게임 상태 브로드캐스트
    - 클라이언트에서 서버에 입력 전송 (이동, 공격, 스킬, 재시작 등)
    - 서버에서 게임 로직 처리 (슬라임 이동, 충돌, 점수 계산 등)
- 생존 시간 기반 클리어 판정 (1분 생존 시 클리어 처리)
- 슬라임 자동 생성 및 추적 로직


<br><br>


## 🌐 서버-클라이언트 통신 구조

- 클라이언트 <code>GameClient</code>에서 <code>Socket</code>으로 서버에 연결하여 플레이어 입력(이동, 공격, 스킬 등)을 전송
- 서버 <code>GameServer</code>에서 모든 클라이언트로부터 입력을 받아 게임 로직을 처리
- 모든 클라이언트에게 게임 최신 상태(위치, 몬스터, 총알, 점수 등)를 브로드캐스트
- 클라이언트에서 해당 정보를 기반으로 게임 화면을 갱신


<br><br>


## **⚒️ 사용 언어 및 툴**
- **Java Swing**
- **Java**


<br><br>


## 👨🏻‍💻 팀원

- <a href="https://github.com/SongHyojun0228" target="_blank">송효준</a>
- <a href="https://github.com/walwal123" target="_blank">유승원</a>
- <a href="https://github.com/orca-java01" target="_blank">정준영</a>

<br><br>


## 📈 성장점
- Java Swing으로 화면을 구성하며 흐름에 맞는 이벤트 처리와 화면 렌더링 방식에 대해 배울 수 있었습니다.
- **Socket 통신을 사용해 플레이어 입력 → 서버 처리 → 상태 브로드캐스트 흐름을 구현하며, 멀티스레드 서버 설계와 실시간 통신 구조에 대한 이해를 키울 수 있었습니다.**
- 슬라임 자동 생성, 추적, 충돌 처리 등 게임 로직을 직접 설계하고 객체화하며 게임 개발의 기초 익힐 수 있었습니다.
- **클라이언트-서버 간의 역할 분리, 데이터 처리 흐름, 지연과 동기화 문제 해결 등 여러 어려움들을 경험할 수 있었습니다.**


<br><br>


## 📸 프로젝트 사진
### 🏠 메인 화면
<img width="1276" alt="Image" src="https://github.com/user-attachments/assets/f17275d9-c623-47a2-86c5-656d2f187f68" />

---

### 🎞️ 스크린샷
| 스크린샷1 | 스크린샷2 |
|:-:|:-:|
| <img width="1920" alt="Image" src="https://github.com/user-attachments/assets/7304c065-b145-45b4-9168-58a303c0e014" /> | <img width="1920" alt="Image" src="https://github.com/user-attachments/assets/3d61367e-1a1f-4367-848f-b58f55b4d17e" /> |

---

### 🎞️ 스크린샷
| 스크린샷3 | 스크린샷4 |
|:-:|:-:|
| <img width="1919" alt="Image" src="https://github.com/user-attachments/assets/2af4656c-3d92-44e4-bfeb-fd53000ea395" /> | <img width="288" alt="Image" src="https://github.com/user-attachments/assets/69be57af-ec05-4169-aafd-4bfa2fb3f095" /> |

---

### 🔚 종료화면
<img width="1920" alt="Image" src="https://github.com/user-attachments/assets/4b2d3d63-5252-4b07-8dd5-716bf59c752e" />
