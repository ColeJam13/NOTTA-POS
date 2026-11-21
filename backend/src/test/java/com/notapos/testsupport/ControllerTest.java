package com.notapos.testsupport;

import com.notapos.security.StaffSessionStore;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Put this on a controller test in place of @WebMvcTest.
 *
 *     @ControllerTest(StaffController.class)
 *     class StaffControllerTest {
 *
 *         @Autowired private MockMvc mockMvc;
 *         @Autowired private SignIn signIn;
 *
 *         mockMvc.perform(get("/api/staff").with(signIn.asManager()))
 *
 * WHAT: It is @WebMvcTest (start only the web layer: the named controller,
 *       the error handler, the request checks; services are faked with
 *       @MockBean) plus the pieces sign-in needs, so every controller test
 *       runs with the real "who may do this" check and can say who is asking.
 *
 * WHY:  @WebMvcTest on its own does not load ordinary @Component classes, and
 *       the list of who is signed in (StaffSessionStore) is one. Rather than
 *       have every test remember what to import, the list is here once. When
 *       how sign-in works changes, this file and SignIn change, and the tests
 *       do not.
 *
 * HOW:  An annotation that carries other annotations is called a "composed"
 *       annotation. @AliasFor passes the controller class given here on to
 *       @WebMvcTest, as if it had been written there.
 *
 * To act as a signed-in person in a test, see SignIn.
 *
 * @author CJ
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@WebMvcTest
@Import({StaffSessionStore.class, SignIn.class})
public @interface ControllerTest {

    @AliasFor(annotation = WebMvcTest.class, attribute = "controllers")
    Class<?>[] value() default {};              // The controller under test
}
