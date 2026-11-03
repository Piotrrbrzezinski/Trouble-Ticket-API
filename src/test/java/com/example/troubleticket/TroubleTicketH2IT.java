package com.example.troubleticket;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "app.jwt.secret=integration-test-secret-minimum-32-bytes-long",
        "app.jwt.issuer=trouble-ticket-local",
        "spring.datasource.url=jdbc:h2:mem:trouble_ticket_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
@AutoConfigureMockMvc
class TroubleTicketH2IT extends TroubleTicketApiIT {
}
