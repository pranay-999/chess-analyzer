package com.chessanalyzer.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * The React app is a "single-page application" (SPA): there is only ONE real HTML
 * file, static/index.html, and React draws the right page based on the URL.
 *
 * Clicking links inside the app never asks the server. But if you refresh the
 * browser on /games/42, the browser asks Spring for "/games/42", a file that doesn't
 * exist. This controller answers such URLs with index.html (a server-side "forward",
 * the URL in the browser stays /games/42), and React then shows game 42.
 *
 * @Controller (not @RestController): the returned String is a view name, and the
 * "forward:" prefix means "serve this other path instead".
 */
@Controller
public class SpaController {

    @GetMapping({"/games/{id}", "/analyze", "/import", "/play", "/puzzles", "/stats", "/login", "/online", "/online/{id}"})
    public String page() {
        return "forward:/index.html";
    }
}
