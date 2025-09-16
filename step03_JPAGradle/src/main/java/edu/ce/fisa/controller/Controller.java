package edu.ce.fisa.controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
@RestController
@RequestMapping("app")
public class Controller {
	
	@GetMapping("/get")
	public String getReqRes() {
		return "get 방식 요청의 응답 데이터 : 나는동민";
	}
	
	@PostMapping("/post")
	public String getReqRes2() {
		return "post 방식 요청의 응답 데이터 : 나는솔로";
	}
}