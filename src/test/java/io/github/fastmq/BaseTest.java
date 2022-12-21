package io.github.fastmq;

import static org.junit.Assert.assertTrue;

import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * Unit test for simple App.
 */
@SpringBootTest(classes = BaseTest.class)
@RunWith(SpringRunner.class)
@ComponentScan("com.fastmq")
public class BaseTest {
}
