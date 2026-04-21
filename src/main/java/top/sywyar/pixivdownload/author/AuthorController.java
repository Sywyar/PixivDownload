package top.sywyar.pixivdownload.author;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/authors")
@RequiredArgsConstructor
public class AuthorController {

    private final AuthorService authorService;

    @GetMapping
    public List<Author> getAuthors() {
        return authorService.getAllAuthors();
    }

    @GetMapping("/paged")
    public AuthorService.PagedAuthors getPagedAuthors(
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "name") String sort) {
        return authorService.getPagedAuthorsWithArtworks(page, size, search, sort);
    }
}
