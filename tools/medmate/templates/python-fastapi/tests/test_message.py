from app.message import message


def test_message_includes_the_app_name() -> None:
    assert message("__NAME__") == "Hello from __NAME__"
