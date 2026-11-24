package in.medmate.__NAME__;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {
  @GetMapping("/")
  public String home() {
    return Message.text("__NAME__");
  }
}
