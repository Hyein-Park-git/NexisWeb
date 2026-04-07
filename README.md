# NexisWEB/WAS

**NEXIS Monitoring Platform**
<br><br>

---

## 설치 파일 다운로드

| OS | 파일 | 크기 |
|---|---|---|
| Windows | [`NexisServer_Setup_v1.exe`](https://drive.google.com/file/d/1Lw-oJwMu_aeD_Clv3_C6zlBUfhukT-cU/view?usp=drive_link) | 11.98MB |
| Linux | [`nexis-server-1.0-1.noarch.rpm`](https://drive.google.com/file/d/1v1sgtnpRE4uaNzCUMJT9vUMvZ2VfVSgj/view?usp=drive_link) | 9.10MB |
 
<br><br>

---

## 설치 방법(Windows)
> 자세한 내용은 [티스토리](https://hailey-p.tistory.com/22) 참조
<br><br>
 
1. `NexisWeb_Setup_v1.exe` 실행 *(관리자 권한으로 실행)*
 
2. 설치 경로 지정
 
3. WEB Port 및 로그 경로 지정
 
4. DB 정보 입력
 
5. Nexis Server 정보 입력
 
6. 설치 전 선택 항목 확인
 
7. 설치 완료 및 바로 시작 여부 확인
 
    > 사전에 반드시 Nexis DB Setup 완료 후 실행 필요
 
8. 정상 실행 시 트레이에서 확인 가능
 
9. 설치 경로에서 `nexis.conf` 확인
 
10. 로그 위치에서 로그 확인 가능
 
11. 종료 시 트레이 아이콘에서 Exit
 
<br><br>
 
---
 
## 설치 방법 (Linux)
 
1. `nexis-web-1.0-1.noarch.rpm` 파일 확인
 
2. rpm 설치
```bash
rpm -ivh nexis-web-1.0-1.noarch.rpm
```
 
3. 설치 완료 시 `/etc/systemd/system/nexis-web.service` 자동 등록
 
4. 설치 디렉토리 `/opt/nexis-web` 내 `nexis.conf` 확인
 
5. nexis-web 실행 및 프로세스 확인
```bash
systemctl start nexis-web
systemctl status nexis-web
```
 
6. 설치 시 지정한 경로에서 로그 확인 가능

<br><br>

---

## 둘러보기

## 1. Login

<img width="1280" height="819" alt="1" src="https://github.com/user-attachments/assets/89ad98d5-886b-46fd-8b4e-336ea8679bea" />
<br><br>


---
## 2. Setup Wizard

<img width="1201" height="802" alt="다운로드" src="https://github.com/user-attachments/assets/81a4d919-131f-43d1-b78a-d7220696e8fa" />

<br><br>

---

## 3. Dashboard
<img width="1280" height="819" alt="다운로드" src="https://github.com/user-attachments/assets/c3387d1b-3720-4c32-810d-7ddc440cc0a9" />
<br><br>

---

## 4. Hosts

* 에이전트 통신 확인 시 호스트 자동 등록
* 갱신 간격 기준 **2회 데이터 미수집 시 Inactive 처리**
<img width="1278" height="820" alt="host" src="https://github.com/user-attachments/assets/fa979929-bef5-4090-8284-645cbd6348c5" />


### 호스트 상세

* 호스트 그룹 설정
* 템플릿 할당
* 아이템 / 트리거 생성 가능
<img width="1280" height="819" alt="4" src="https://github.com/user-attachments/assets/a2268f7e-2d82-46f5-8071-d8a60f7e468f" />
<br><br>

---

## 5. Latest Data

* 등록된 호스트 기준 최신 데이터 확인 가능

<img width="1280" height="819" alt="5" src="https://github.com/user-attachments/assets/0edfdca6-5a44-4c28-b23a-3b5b22d0bc3f" />

### 그래프 보기

<img width="1280" height="843" alt="6" src="https://github.com/user-attachments/assets/97302b46-a6fb-46e5-a38f-0b13d7478529" />

### 차트 보기

<img width="1280" height="2346" alt="7" src="https://github.com/user-attachments/assets/6ac5d5fe-38c4-45c5-90f6-064522e2ea45" />
<br><br>

---

## 6. Problems

<img width="1277" height="817" alt="problem" src="https://github.com/user-attachments/assets/0c7150ff-9e95-4f40-9fd5-84a59ab2d6f1" />
<br><br>

---

## 7. Templates

<img width="1280" height="817" alt="9" src="https://github.com/user-attachments/assets/d4082bce-b5a2-434a-875e-e074e8d4cf89" />
<img width="1280" height="819" alt="10" src="https://github.com/user-attachments/assets/a0878ffe-384f-46c2-97ea-c34d5724f9c6" />
<br><br>

---

## 8. Host Groups

* 최초 설치 시 **Default Group 자동 생성**
* 자동 등록된 호스트는 해당 그룹에 포함됨
<img width="1280" height="820" alt="11" src="https://github.com/user-attachments/assets/5b9fa7b9-4985-4e9e-9b9b-8ea4c3bbb4d2" />
<img width="1280" height="817" alt="12" src="https://github.com/user-attachments/assets/e9991978-7150-434b-83db-2e8bbff39c3d" />
<br><br>

---

## 9. Users

<img width="1280" height="817" alt="13" src="https://github.com/user-attachments/assets/6fe26af2-8b09-47ce-8500-b6173943c6f3" />
<img width="1280" height="843" alt="14" src="https://github.com/user-attachments/assets/d03a873a-777b-47c9-b8ae-f5c5f59e0104" />
<br><br>

---

## 10. User Groups

* 사용자 그룹 추가 가능
* 그룹별 상세 권한 설정 가능
<img width="1280" height="811" alt="15" src="https://github.com/user-attachments/assets/b8107831-65df-44b9-9586-350a88e6898a" />
<img width="1280" height="810" alt="16" src="https://github.com/user-attachments/assets/42574fa6-be7b-40ca-a9cc-de32110f5bd8" />
<br><br>

---

## 11. Setting

* **Administrator만 접근 가능**
<img width="1280" height="833" alt="17" src="https://github.com/user-attachments/assets/47a6215d-e7b7-4626-a96f-dfe0d66b43b6" />
<br><br>
