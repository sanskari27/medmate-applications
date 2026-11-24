from fastapi.testclient import TestClient

from app.main import app


def test_home_returns_the_message() -> None:
    response = TestClient(app).get("/")
    assert response.status_code == 200
    assert response.json()["message"].startswith("Hello from ")
