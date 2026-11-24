import express from "express";
import { message } from "./message.js";

const app = express();
const port = Number(process.env.PORT ?? "__PORT__");

app.get("/", (_request, response) => {
  response.type("text/plain").send(message("__NAME__"));
});

app.listen(port, "0.0.0.0");
