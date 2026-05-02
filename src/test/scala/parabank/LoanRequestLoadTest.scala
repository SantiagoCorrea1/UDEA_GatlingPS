package parabank

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

class LoanRequestLoadTest extends Simulation {

  // Configuración HTTP base
  val httpConf = http
    .baseUrl("https://parabank.parasoft.com/parabank")
    .acceptHeader("application/json, text/html, */*")
    .userAgentHeader("Gatling Load Test")
    .contentTypeHeader("application/x-www-form-urlencoded")

  // Feeders CSV
  val userFeeder = csv("data/users.csv").circular
  val feeder = csv("data/loans.csv").circular

  // Paso 1: Login
  val login = feed(userFeeder)
    .exec(
      http("Login")
        .post("/login.htm")
        .formParam("username", "${username}")
        .formParam("password", "${password}")
        .check(status.in(200, 302))
    )

  // Paso 2: Solicitud de préstamo (ya autenticado)
  val requestLoan = feed(feeder)
    .exec(
      http("Request Loan")
        .post("/services_proxy/bank/requestLoan")
        .queryParam("customerId", "${customerId}")
        .queryParam("fromAccountId", "${fromAccountId}")
        .queryParam("amount", "${amount}")
        .queryParam("downPayment", "${downPayment}")
        .check(status.is(200))
    )

  // Escenario principal
  val scn = scenario("Solicitud de préstamo bajo carga")
    .exec(login)
    .pause(3)
    .exec(requestLoan)

  // Inyección de usuarios
  setUp(
    scn.inject(rampUsers(10).during(30.seconds))
  ).protocols(httpConf)
   .assertions(
     global.responseTime.mean.lte(8000),
     global.successfulRequests.percent.gte(90)
   )
}


