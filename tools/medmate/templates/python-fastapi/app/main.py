import os

from fastapi import FastAPI

from app.message import message

app = FastAPI()


@app.get("/")
def home() -> dict[str, str]:
    return {"message": message("__NAME__")}


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=int(os.environ.get("PORT", "__PORT__")))
