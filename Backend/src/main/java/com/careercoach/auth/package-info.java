/**
 * Module {@code auth} — Google OAuth2 login, email-whitelisted, single-user
 * identity (BACKEND_DESIGN §2.1, TECHNICAL_DESIGN §7).
 *
 * <p>Layered: {@code web} (controllers/DTOs), {@code service} (application
 * services), {@code domain} (entities/value types), {@code repository}
 * (Spring Data), {@code config} (Spring wiring).
 */
package com.careercoach.auth;
