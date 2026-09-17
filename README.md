# 무브온(MoveOn)

소규모 공연 예매 MSA + MySQL HA 학습 프로젝트.

## 실행

```bash
cp .env.example .env   # 값 채우기
docker compose up --build
curl http://localhost:8080/concerts
```

상세 순서는 [GUIDE.md](./GUIDE.md) 참고.
