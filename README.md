# Java_Swing_Mini_Game : SLIME SURVIVAL

<img width="1276" alt="Image" src="https://github.com/user-attachments/assets/f17275d9-c623-47a2-86c5-656d2f187f68" />

<br>
<br>

<h2>📌 프로젝트 소개</h2>
<h3>자바 스윙을 이용한 미니 게임</h3>
<p>- Java Swing과 Socket 통신을 활용해 제작한 2D 멀티플레이 생존 게임</p>
<p>- 실시간 통신 구조 설계 및 구현 경험</p>
<p>- 클라이언트 측 이벤트 처리 및 UI 표현</p>
<br>
<p>- 이 프로젝트는 단순한 게임 구현을 넘어, 실시간 서버-클라이언트 구조와 UI 렌더링, 사용자 입력 처리, 충돌 판정, 애니메이션 처리 등 **게임 개발 전반에 필요한 핵심 로직**을 직접 설계하고 구현한 경험을 담고 있습니다.</p>

<br>
<br>

<h2>📌 주요기능</h2>
<p>- 🎮 Java Swing 기반의 GUI 게임 구현</p>
<p>- 👾 슬라임 몬스터 공격 및 생존</p>
<p>- 🔫 기본 공격(Z key), 스킬(X key)​​</p>
<p>- 💥 스킬 이펙트, 사망 애니메이션, 점수 시스템 구현</p>
<p>- 🗣️ 서버-클라이언트 멀티플레이 기능</p>
<p>&nbsp;&nbsp;&nbsp;&nbsp;- Socket, ObjectStream 기반 실시간 통신</p>
<p>&nbsp;&nbsp;&nbsp;&nbsp;- 플레이어 입력 → 서버 처리 → 모든 클라이언트에 게임 상태 브로드캐스트</p>
<p>&nbsp;&nbsp;&nbsp;&nbsp;- 클라이언트에서 서버에 입력 전송 (이동, 공격, 스킬, 재시작 등)</p>
<p>&nbsp;&nbsp;&nbsp;&nbsp;- 서버에서 게임 로직 처리 (슬라임 이동, 충돌, 점수 계산 등)</p>
<p>- 🏁 생존 시간 기반 클리어 판정 (1분 생존 시 클리어 처리)</p>
<p>- 🧟 슬라임 자동 생성 및 추적 로직</p>

<br>
<br>

<h2>📸 프로젝트 사진</h2>
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

<br>
<br>
<br>

<h2>🛠️ 설치 및 실행 방법</h2>
<h3>1️⃣ 프로젝트 클론</h3>

```
git clone https://github.com/SongHyojun0228/Mensa.git
cd Mensa
```

<h3>2️⃣ 서버 실행</h3>

```
javac multi/GameServer.java
java multi.GameServer
```

<h3>3️⃣ 클라이언트 실행</h3>

```
javac multi/GameMainMenu.java
java multi.GameMainMenu
```

<br>
<br>

<h2>🌐 서버-클라이언트 통신 구조</h2>
<p>- 클라이언트[GameClient]는 [Socket]으로 서버에 연결하여 플레이어 입력(이동, 공격, 스킬 등)을 전송</p>
<p>- 서버[GameServer]는 모든 클라이언트로부터 입력을 받아 게임 로직을 처리</p>
<p>- 모든 클라이언트에게 최신 게임 상태(위치, 몬스터, 총알, 점수 등)를 브로드캐스트</p>
<p>- 클라이언트는 해당 정보를 기반으로 게임 화면을 갱신</p>

<br>
<br>

<h2>👨🏻‍💻 기여자</h2>
<p><a href="https://github.com/SongHyojun0228" target="_blank">송효준</a></p>
<p><a href="https://github.com/walwal123" target="_blank">유승원</a></p>
<p><a href="https://github.com/orca-java01" target="_blank">정준영</a></p>

