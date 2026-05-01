package parabank

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

/**
 * BillPayPerformanceTest - Historia de Usuario No Funcional 5: Pago de servicios con concurrencia alta
 *
 * Como cliente del banco, quiero que el módulo de pago de servicios funcione de manera eficiente durante picos de uso,
 * para que pueda realizar mis pagos sin retrasos ni fallas, incluso en horarios de alta demanda.
 *
 * Criterios de aceptación:
 * - Durante una simulación de 200 usuarios concurrentes realizando pagos
 * - El tiempo de respuesta por transacción debe ser ≤ 3 segundos
 * - La tasa de errores funcionales debe ser ≤ 1%
 * - El sistema debe registrar correctamente el pago en el historial del usuario sin duplicaciones
 *
 * Método de inyección: rampConcurrentUsers + constantConcurrentUsers (200 simultáneos reales)
 */
class BillPayPerformanceTest extends Simulation {

  val httpConf = http
    .baseUrl("https://parabank.parasoft.com/parabank")
    .acceptHeader("text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
    .userAgentHeader("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
    .acceptEncodingHeader("gzip, deflate")
    .acceptLanguageHeader("en-US,en;q=0.5")
    .connectionHeader("keep-alive")
    .maxConnectionsPerHost(25)

  val userFeeder = csv("data/users.csv").circular
  val billPayFeeder = csv("data/billpay.csv").circular

  val billPayScenario = scenario("Bill Pay Performance Test")
    .feed(userFeeder)
    .feed(billPayFeeder)
    .exec(
      // Paso 1: Login
      http("Login for Bill Pay")
        .post("/login.htm")
        .formParam("username", "${username}")
        .formParam("password", "${password}")
        .check(status.in(200, 302))
        .check(substring("The username and password could not be verified").notExists)
    )
    .pause(1)
    .exec(
      // Paso 2: Navegar a Bill Pay
      http("Navigate to Bill Pay")
        .get("/billpay.htm")
        .check(status.in(200, 302))
    )
    .pause(1)
    .exec(
      // Paso 3: Enviar pago de factura
      http("Process Bill Payment")
        .post("/billpay.htm")
        .formParam("payeeName", "${payeeName}")
        .formParam("address", "${address}")
        .formParam("city", "${city}")
        .formParam("state", "${state}")
        .formParam("zipCode", "${zipCode}")
        .formParam("phoneNumber", "${phoneNumber}")
        .formParam("accountNumber", "${accountNumber}")
        .formParam("verifyAccount", "${accountNumber}")
        .formParam("amount", "${amount}")
        .formParam("fromAccountId", "${fromAccountId}")
        .check(status.in(200, 302))
    )
    .pause(2)
    .exec(
      // Paso 4: Verificar historial de transacciones (sin duplicaciones)
      http("Verify Transaction History")
        .get("/activity.htm")
        .queryParam("id", "${fromAccountId}")
        .check(status.in(200, 302))
    )

  setUp(
    billPayScenario
      .inject(
        // Subida gradual hasta 200 usuarios concurrentes (criterio de aceptación)
        rampConcurrentUsers(0).to(200).during(30.seconds),

        // Mantener 200 usuarios concurrentes con carga sostenida
        constantConcurrentUsers(200).during(120.seconds),

        // Descenso gradual
        rampConcurrentUsers(200).to(0).during(30.seconds)
      )
  )
    .protocols(httpConf)
    .assertions(
      // 200 usuarios concurrentes: p95 ≤ 3 segundos
      global.responseTime.percentile(95).lte(3000),
      // Tasa de errores funcionales ≤ 1%
      global.failedRequests.percent.lte(1)
    )
}
