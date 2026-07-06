import { serve } from "@hono/node-server";
import { Hono } from "hono";

const app = new Hono();

app.get("/", (context) => context.text("ServerAnnounce backend"));

const port = Number.parseInt(process.env.PORT ?? "3000", 10);

serve({
  fetch: app.fetch,
  port,
});
