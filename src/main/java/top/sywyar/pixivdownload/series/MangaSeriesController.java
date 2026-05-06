package top.sywyar.pixivdownload.series;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.sywyar.pixivdownload.gallery.GalleryRepository;
import top.sywyar.pixivdownload.gallery.GuestRestriction;
import top.sywyar.pixivdownload.setup.guest.GuestAccessGuard;
import top.sywyar.pixivdownload.setup.guest.GuestInviteSession;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/series")
@RequiredArgsConstructor
public class MangaSeriesController {

    private final MangaSeriesService mangaSeriesService;
    private final GalleryRepository galleryRepository;

    @GetMapping
    public List<MangaSeries> getSeries(HttpServletRequest httpRequest) {
        Set<Long> filter = resolveGuestFilter(httpRequest);
        return mangaSeriesService.getAllSeries(filter);
    }

    @GetMapping("/paged")
    public MangaSeriesService.PagedSeries getPagedSeries(
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "title") String sort,
            HttpServletRequest httpRequest) {
        Set<Long> filter = resolveGuestFilter(httpRequest);
        return mangaSeriesService.getPagedSeriesWithArtworks(page, size, search, sort, filter);
    }

    @GetMapping("/{seriesId}")
    public ResponseEntity<MangaSeriesDetail> getSeriesDetail(
            @PathVariable long seriesId,
            HttpServletRequest httpRequest) {
        GuestInviteSession session = GuestAccessGuard.extractSession(httpRequest);
        Set<Long> filter = resolveGuestFilter(session);
        if (filter != null && !filter.contains(seriesId)) {
            return ResponseEntity.notFound().build();
        }
        MangaSeriesDetail detail = mangaSeriesService.getSeriesDetail(seriesId);
        if (detail == null) {
            return ResponseEntity.notFound().build();
        }
        // 访客可见的章节数 ≠ 系列章节总数；返回前者，避免泄漏访客看不到的章节存在性。
        if (session != null) {
            long visibleCount = galleryRepository.countArtworksInSeries(seriesId, GuestRestriction.from(session));
            detail = new MangaSeriesDetail(
                    detail.seriesId(),
                    detail.title(),
                    detail.authorId(),
                    detail.authorName(),
                    visibleCount,
                    detail.updatedTime()
            );
        }
        return ResponseEntity.ok(detail);
    }

    private Set<Long> resolveGuestFilter(HttpServletRequest httpRequest) {
        return resolveGuestFilter(GuestAccessGuard.extractSession(httpRequest));
    }

    private Set<Long> resolveGuestFilter(GuestInviteSession session) {
        if (session == null) return null;
        return galleryRepository.findVisibleSeriesIds(GuestRestriction.from(session));
    }
}
