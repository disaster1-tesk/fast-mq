package io.github.fastmq.resource;

import io.github.fastmq.infrastructure.http.HttpResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

@RequestMapping("/fast/mq/auth")
@Controller
public class AuthResource {

    @Value("${fast.config.auth.username:fastmq}")
    public String username;
    @Value("${fast.config.auth.password:fastmq}")
    public String password;


    @PutMapping("/login/{username}/{password}")
    @ResponseBody
    public HttpResult auth(@PathVariable("username") String username, @PathVariable("password") String password) {
        if (username.equals(this.username) && password.equals(this.password)) {
            return HttpResult.success("登录成功");
        }
        return HttpResult.fail("账号/密码 错误");
    }
}
