package parabank

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

/**
 * LoginPerformanceTest - Historia de Usuario No Funcional 1: Tiempo de respuesta en login
 *
 * Como usuario del banco, quiero que el sistema procese mi inicio de sesión en menos de 2 segundos bajo carga normal,
 * para que pueda acceder rápidamente a mi cuenta sin demoras innecesarias.
 *
 * Criterios de aceptación:
 * - El tiempo de respuesta para el login debe ser ≤ 2 segundos con hasta 100 usuarios concurrentes
 * - Bajo carga pico (200 usuarios), el tiempo no debe superar los 5 segundos
 *
 * Método de inyección: constantConcurrentUsers + rampConcurrentUsers
 */
class LoginPerformanceTest extends Simulation {

  val httpConf = http
    .baseUrl("https://parabank.parasoft.com/parabank")
    .acceptHeader("text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
    .userAgentHeader("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
    .acceptEncodingHeader("gzip, deflate")
    .acceptLanguageHeader("en-US,en;q=0.5")
    .connectionHeader("keep-alive")
    .maxConnectionsPerHost(20)

  val userFeeder = csv("data/users.csv").circular

  val loginScenario = scenario("Login Performance Test")
    .feed(userFeeder)
    .exec(
      http("Login Request")
        .post("/login.htm")
        .formParam("username", "${username}")
        .formParam("password", "${password}")
        .check(status.in(200, 302))
        .check(substring("The username and password could not be verified").notExists)
    )

  setUp(
    loginScenario
      .inject(
        // Carga normal: 100 usuarios concurrentes
        constantConcurrentUsers(100).during(60.seconds),

        // Escalado hacia carga pico
        rampConcurrentUsers(100).to(200).during(30.seconds),

        // Carga pico: 200 usuarios concurrentes
        constantConcurrentUsers(200).during(60.seconds),

        // Descenso gradual
        rampConcurrentUsers(200).to(0).during(30.seconds)
      )
  )
    .protocols(httpConf)
    .assertions(
      // Carga normal (100 usuarios): promedio ≤ 2 segundos
      global.responseTime.mean.lte(2000),
      // Carga pico (200 usuarios): p95 ≤ 5 segundos
      global.responseTime.percentile(95).lte(5000),
      // Tasa de éxito mínima para detectar fallos funcionales
      global.successfulRequests.percent.gte(95)
    )
}
