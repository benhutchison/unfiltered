package unfiltered.netty

import org.specs2.mutable.{BeforeAfter, Specification}

import unfiltered.response.{ Pass, Ok, ResponseString }
import unfiltered.request.{ GET, Params, Path => UFPath, POST, PUT, RemoteAddr, & }


object ServerSpec extends Specification with unfiltered.specs2.netty.Served {

  def setup = _.plan(planify({
    case GET(UFPath("/pass")) => Pass
    case GET(UFPath("/")) =>
      ResponseString("test") ~> Ok
    case r @ GET(UFPath("/addr")) => ResponseString(r.remoteAddr) ~> Ok
    case GET(UFPath("/addr_extractor") & RemoteAddr(addr)) => ResponseString(addr) ~> Ok
  })).plan(async.Planify({
    case GET(UFPath("/pass")) => Pass
    case req @ GET(UFPath("/planc")) =>
      req.underlying.respond(ResponseString("planc") ~> Ok)
  })).plan(planify({
    case GET(UFPath("/planb")) => ResponseString("planb") ~> Ok
    case GET(UFPath("/pass")) => ResponseString("pass") ~> Ok
  })).plan(planify({
    case req @ UFPath("/params") & Params(p) & (POST(_) | PUT(_)) =>
      Ok ~> ResponseString(req.method + ":" + p.map { case (k, vs) => vs.map(k + "=" + _).mkString("&") }.mkString("&"))
  }))

  "A Server" should {
    "respond to requests" in {
      http(host as_str) must_== "test"
    }
    "provide a remote address" in {
      http(host / "addr" as_str) must_== "127.0.0.1"
    }
    "provide a remote address accounting for X-Forwared-For header" in {
      http(host / "addr_extractor" <:< Map("X-Forwarded-For" -> "66.108.150.228") as_str) must_== "66.108.150.228"
    }
    "provide a remote address accounting for X-Forwared-For header filtering private addresses" in {
      http(host / "addr_extractor" <:< Map("X-Forwarded-For" -> "172.31.255.255") as_str) must_== "127.0.0.1"
    }
    "respond to requests in ustream channel plan" in {
      http(host / "planc" as_str) must_== "planc"
    }
    "respond to requests in last channel handler" in {
      http(host / "planb" as_str) must_== "planb"
    }
    "pass upstream on Pass, respond in last handler" in {
      http(host / "pass" as_str) must_== "pass"
    }
    "echo POST parameters encoded in the entity body" in {
      http(host / "params" << Map("n0" -> "v0") as_str) must_== "POST:n0=v0"
    }
    "echo PUT paremters encoded in the entity body" in {
      http((host / "params" << Map("n0" -> "v0") PUT) as_str) must_== "PUT:n0=v0"
    }
  }
}
