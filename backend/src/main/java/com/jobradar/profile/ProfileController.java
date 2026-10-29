package com.jobradar.profile;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@RestController
class ProfileController {
    // No & = ? #: titles go into board query strings unescaped.
    private static final Pattern TITLE = Pattern.compile("[\\p{L}\\p{N} ,./+()'-]{2,80}");
    private static final int MAX_ITEMS = 20, MAX_NOTES = 2000, MAX_RESUME = 50_000;

    private final ProfileStore store;

    ProfileController(ProfileStore store) { this.store = store; }

    @GetMapping("/api/profile")
    Map<String, Object> get() {
        String resume = store.resume();
        return Map.of("profile", store.get(), "resumeChars", resume.length(),
                "resumeStart", resume.substring(0, Math.min(300, resume.length())));
    }

    @PutMapping(path = "/api/profile", consumes = MediaType.APPLICATION_JSON_VALUE)
    Profile save(@RequestBody Profile p) {
        if (p.titles().isEmpty()) throw bad("add at least one job title");
        for (var t : p.titles()) if (!TITLE.matcher(t).matches()) throw bad("job title has odd characters: " + t);
        for (var l : p.locations()) if (!TITLE.matcher(l).matches()) throw bad("location has odd characters: " + l);
        for (var l : List.of(p.titles(), p.locations(), p.exclude()))
            if (l.size() > MAX_ITEMS || l.stream().anyMatch(s -> s.length() > 80)) throw bad("lists hold up to 20 short items");
        if (p.notes().length() > MAX_NOTES) throw bad("notes are capped at " + MAX_NOTES + " characters");
        if (p.minPay() < 0) throw bad("minimum pay can't be negative");
        store.save(p);
        return p;
    }

    /** PDF or plain text; stored as text so scoring never re-parses. */
    @PutMapping(path = "/api/profile/resume", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    Map<String, Integer> resume(@RequestParam MultipartFile file) throws IOException {
        byte[] bytes = file.getBytes();
        String text;
        if (bytes.length > 4 && new String(bytes, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")) {
            try (var doc = Loader.loadPDF(bytes)) { text = new PDFTextStripper().getText(doc); }
            catch (IOException e) { throw bad("couldn't read that PDF"); }
        } else {
            text = new String(bytes, StandardCharsets.UTF_8);
        }
        text = text.strip();
        if (text.length() < 200) throw bad("resume has under 200 characters of text; is it a scanned image?");
        if (text.length() > MAX_RESUME) throw bad("resume text is over " + MAX_RESUME + " characters");
        store.saveResume(text);
        return Map.of("resumeChars", text.length());
    }

    private static ResponseStatusException bad(String msg) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg); }
}
