FROM --platform=$BUILDPLATFORM maven:3.9-eclipse-temurin-21 AS dev
WORKDIR /src
COPY pom.xml .
COPY pharmacy_server_shared/pom.xml pharmacy_server_shared/pom.xml
COPY pharmacy_server_identity/pom.xml pharmacy_server_identity/pom.xml
COPY pharmacy_server_support/pom.xml pharmacy_server_support/pom.xml
COPY pharmacy_server_employees/pom.xml pharmacy_server_employees/pom.xml
COPY pharmacy_server_distributors/pom.xml pharmacy_server_distributors/pom.xml
COPY pharmacy_server_catalog/pom.xml pharmacy_server_catalog/pom.xml
COPY pharmacy_server_stock/pom.xml pharmacy_server_stock/pom.xml
COPY pharmacy_server_offers/pom.xml pharmacy_server_offers/pom.xml
COPY pharmacy_server_billing/pom.xml pharmacy_server_billing/pom.xml
COPY pharmacy_server_crm/pom.xml pharmacy_server_crm/pom.xml
COPY pharmacy_server_expenses/pom.xml pharmacy_server_expenses/pom.xml
COPY pharmacy_server_reports/pom.xml pharmacy_server_reports/pom.xml
COPY pharmacy_server_dashboard/pom.xml pharmacy_server_dashboard/pom.xml
COPY pharmacy_server_leads/pom.xml pharmacy_server_leads/pom.xml
COPY pharmacy_server_departments/pom.xml pharmacy_server_departments/pom.xml
COPY pharmacy_server_wards/pom.xml pharmacy_server_wards/pom.xml
COPY pharmacy_server_doctors/pom.xml pharmacy_server_doctors/pom.xml
COPY pharmacy_server_oversight/pom.xml pharmacy_server_oversight/pom.xml
COPY pharmacy_server_prep/pom.xml pharmacy_server_prep/pom.xml
COPY pharmacy_server_customer/pom.xml pharmacy_server_customer/pom.xml
COPY pharmacy_server_coupons/pom.xml pharmacy_server_coupons/pom.xml
COPY pharmacy_server_app/pom.xml pharmacy_server_app/pom.xml
COPY pharmacy_server_shared/src pharmacy_server_shared/src
COPY pharmacy_server_identity/src pharmacy_server_identity/src
COPY pharmacy_server_support/src pharmacy_server_support/src
COPY pharmacy_server_employees/src pharmacy_server_employees/src
COPY pharmacy_server_distributors/src pharmacy_server_distributors/src
COPY pharmacy_server_catalog/src pharmacy_server_catalog/src
COPY pharmacy_server_stock/src pharmacy_server_stock/src
COPY pharmacy_server_offers/src pharmacy_server_offers/src
COPY pharmacy_server_billing/src pharmacy_server_billing/src
COPY pharmacy_server_crm/src pharmacy_server_crm/src
COPY pharmacy_server_expenses/src pharmacy_server_expenses/src
COPY pharmacy_server_reports/src pharmacy_server_reports/src
COPY pharmacy_server_dashboard/src pharmacy_server_dashboard/src
COPY pharmacy_server_leads/src pharmacy_server_leads/src
COPY pharmacy_server_departments/src pharmacy_server_departments/src
COPY pharmacy_server_wards/src pharmacy_server_wards/src
COPY pharmacy_server_doctors/src pharmacy_server_doctors/src
COPY pharmacy_server_oversight/src pharmacy_server_oversight/src
COPY pharmacy_server_prep/src pharmacy_server_prep/src
COPY pharmacy_server_customer/src pharmacy_server_customer/src
COPY pharmacy_server_coupons/src pharmacy_server_coupons/src
COPY pharmacy_server_app/src pharmacy_server_app/src

FROM dev AS format
RUN mvn -B spotless:check

FROM dev AS lint
RUN mvn -B spotless:check

FROM dev AS typecheck
RUN mvn -B -DskipTests compile

FROM dev AS unit
RUN mvn -B test

FROM dev AS integration
RUN mvn -B -Dsurefire.skip=true -Dspotless.check.skip=true verify

FROM dev AS build
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre AS runtime
RUN apt-get update \
  && apt-get install -y --no-install-recommends curl \
  && rm -rf /var/lib/apt/lists/* \
  && useradd --system --uid 10001 --home /app app
WORKDIR /app
COPY --from=build /src/pharmacy_server_app/target/pharmacy_server.jar pharmacy_server.jar
USER app
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70"
ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -fsS http://127.0.0.1:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/pharmacy_server.jar"]
