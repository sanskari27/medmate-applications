import { execFileSync } from "node:child_process";

export function digRecords(name, type, server) {
  const output = execFileSync(
    "dig",
    ["+short", type, name, `@${server}`, "+time=3", "+tries=1"],
    { encoding: "utf8" },
  );
  return output
    .split("\n")
    .map((line) => line.trim())
    .filter((line) => line.length > 0)
    .filter((line) => !line.endsWith("."));
}

export function publicIpv4() {
  const output = execFileSync("ip", ["-4", "route", "get", "1.1.1.1"], {
    encoding: "utf8",
  });
  const match = output.match(/\bsrc\s+(\d+\.\d+\.\d+\.\d+)/);
  if (!match) throw new Error("Could not find this server's public IPv4 address");
  return match[1];
}

export function assertDomainPointsHere(domain, resolvers, address) {
  const problems = [];
  for (const resolver of resolvers) {
    const records = digRecords(domain, "A", resolver);
    const wrong = records.filter((record) => record !== address);
    if (records.length === 0 || wrong.length > 0) {
      const seen = records.length > 0 ? records.join(", ") : "no A record";
      problems.push(`${resolver} returned ${seen} for ${domain}`);
    }
  }
  if (problems.length > 0) {
    throw new Error(
      `Deployment stopped: ${domain} does not point at ${address} yet. ${problems.join("; ")}. Add the DNS record, wait until both resolvers see it, then deploy again.`,
    );
  }
}
