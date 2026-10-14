# Claude 사용량 위젯 (Android)

Claude.ai 구독의 **5시간 한도 / 주간 한도** 사용률과 리셋 시간을 홈 화면(DeX 바탕화면 포함)에 표시하는 위젯입니다.

## 빌드
1. Android Studio(Ladybug 이상)에서 이 폴더를 **Open**
2. Gradle 동기화가 끝나면 `Build > Build App Bundle(s) / APK(s) > Build APK(s)`
3. `app/build/outputs/apk/debug/app-debug.apk` 를 폰에 설치
   (설정 > 애플리케이션 > 특별한 접근 > 출처를 알 수 없는 앱 설치 허용)

요구사항: Android 8.0 이상, JDK 17

## 사용법
1. 앱 실행 → **claude.ai 로그인** (이메일 로그인 사용. Google 로그인은 WebView에서 차단됨)
   - 로그인이 어려우면 **세션 키 직접 입력**: PC 브라우저에서 claude.ai 접속 → F12 → Application → Cookies → `sessionKey` 값 복사
2. 홈 화면 빈 곳 길게 누르기 → 위젯 → **Claude 사용량** 추가
3. 위젯 탭 = 즉시 새로고침, 자동 갱신은 15분마다

## 구조
| 파일 | 역할 |
|---|---|
| `UsageApi.kt` | `/api/organizations` → `/api/organizations/{id}/usage` 호출, JSON 파싱 |
| `UsageWorker.kt` | WorkManager 15분 주기 갱신 |
| `UsageWidget.kt` | 위젯 렌더링, 탭 처리 |
| `LoginActivity.kt` | WebView 로그인 후 `sessionKey` 쿠키 추출 |
| `MainActivity.kt` | 로그인/키 입력/로그아웃 화면 |

## 주의
- claude.ai 웹이 내부적으로 쓰는 **비공식 엔드포인트**입니다. 응답 형식이 바뀌면 `UsageApi.parseUsage()`만 수정하세요.
  예상 응답: `{"five_hour":{"utilization":42.0,"resets_at":"..."},"seven_day":{...}}`
- 세션 키는 계정 로그인 정보와 같습니다. 앱 전용 저장소에만 보관하며 claude.ai 외 다른 곳으로 보내지 않습니다. 남과 공유하지 마세요.
