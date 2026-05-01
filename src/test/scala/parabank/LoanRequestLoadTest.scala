package parabank

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

/**
 * LoanRequestLoadTest - Historia de Usuario No Funcional 4: Solicitud de préstamo bajo carga
 *
 * Como usuario del banco, quiero que el sistema procese solicitudes de préstamo de forma rápida y sin errores,
 * incluso cuando muchos usuarios las envían al mismo tiempo, para que pueda obtener una respuesta oportuna.
 *
 * Criterios de aceptación:
 * - Con una carga de 150 usuarios concurrentes realizando solicitudes de préstamo
 * - El tiempo de respuesta promedio debe ser ≤ 5 segundos
 * - El sistema debe mantener una tasa de éxito ≥ 98%
 *
 * Método de inyección: rampConcurrentUsers + constantConcurrentUsers
 */
class LoanRequestLoadTest extends Simulation {

  val httpConf = http
    .baseUrl("https://parabank.parasoft.com/parabank")
    .acceptHeader("application/json, text/html, */*")
    .userAgentHeader("Gatling Load Test")
    .contentTypeHeader("application/x-www-form-urlencoded")

  val userFeeder = csv("data/users.csv").circular
  val feeder = csv("data/loans.csv").circular

  val login = feed(userFeeder)
    .exec(
      http("Login")
        .post("/login.htm")
        .formParam("username", "${username}")
        .formParam("password", "${password}")
        .check(status.in(200, 302))
        .check(substring("The username and password could not be verified").notExists)
    )

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

  val scn = scenario("Solicitud de préstamo bajo carga")
    .exec(login)
    .pause(2)
    .exec(requestLoan)
    .pause(3)

  setUp(
    scn.inject(
      // Subida suave hasta 50 usuarios concurrentes
      rampConcurrentUsers(0).to(50).during(60.seconds),

      // Mantener carga moderada
      constantConcurrentUsers(50).during(60.seconds),

      // Descenso gradual
      rampConcurrentUsers(50).to(0).during(30.seconds)
    )
  ).protocols(httpConf)
   .assertions(
     // Tiempo de respuesta promedio ≤ 5 segundos
     global.responseTime.mean.lte(5000),
     // Tasa de éxito ≥ 98%
     global.successfulRequests.percent.gte(98)
   )
}
