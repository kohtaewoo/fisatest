package edu.ce.fisa.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@org.springframework.stereotype.Controller
public class Controller {
	@GetMapping("/")
	public String home() {
	    return "redirect:/jump/index.html";
	}

	@GetMapping("/health")
	@ResponseBody
	public String health() {
	    return "OK";
	}
	
	@GetMapping("/get")

	public String getReRes1() {
		return "get 방식 요청의 응답 데이터 : 병길";
	}
	@GetMapping("/post")
	public String getReRes2() {
		return "post 방식 요청의 응답 데이터 : 태우";
	}

	// 점수 수신 샘플 (프론트에서 fetch로 호출 가능)
	@PostMapping("/api/score")
	public ResponseEntity<String> score(@RequestParam int value) {
	    // TODO: DB 저장 또는 파일 기록
	    return ResponseEntity.ok("received:" + value);
	}



	public String getReqRes() {
		return "get 방식 요청의 응답 데이터 : 나는병길";
	}
	
	@PostMapping("/post")
	public String getReqRes2() {
		return "post 방식 요청의 응답 데이터 : 나는태우";
	}
}

