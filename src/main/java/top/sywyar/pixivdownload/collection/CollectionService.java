package top.sywyar.pixivdownload.collection;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import top.sywyar.pixivdownload.i18n.AppMessages;
import top.sywyar.pixivdownload.i18n.LocalizedException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class CollectionService {

    public static final int MAX_NAME_LENGTH = 40;

    private final CollectionMapper collectionMapper;
    private final CollectionIconService iconService;
    private final AppMessages messages;

    @PostConstruct
    public void init() {
        collectionMapper.createCollectionsTable();
        collectionMapper.createArtworkCollectionsTable();
        collectionMapper.createArtworkCollectionsArtworkIndex();
    }

    public List<Collection> listAll() {
        return collectionMapper.findAll();
    }

    public Collection get(long id) {
        return collectionMapper.findById(id);
    }

    public boolean exists(long id) {
        return collectionMapper.countById(id) > 0;
    }

    public Collection create(String name) {
        String clean = validateName(name);
        if (collectionMapper.countByName(clean) > 0) {
            throw LocalizedException.badRequest("collection.name.duplicate", "同名收藏夹已存在");
        }
        CollectionInsert insert = new CollectionInsert();
        insert.setName(clean);
        insert.setIconExt(null);
        insert.setSortOrder(0);
        insert.setCreatedTime(Instant.now().getEpochSecond());
        collectionMapper.insert(insert);
        Long newId = insert.getId();
        if (newId == null) {
            throw new LocalizedException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "collection.create.failed",
                    "创建收藏夹失败"
            );
        }
        log.info(message("collection.log.created", newId, clean));
        return collectionMapper.findById(newId);
    }

    public Collection rename(long id, String name) {
        requireExists(id);
        String clean = validateName(name);
        if (collectionMapper.countByNameExcludingId(clean, id) > 0) {
            throw LocalizedException.badRequest("collection.name.duplicate", "同名收藏夹已存在");
        }
        collectionMapper.updateName(id, clean);
        return collectionMapper.findById(id);
    }

    public Collection updateSortOrder(long id, int sortOrder) {
        requireExists(id);
        collectionMapper.updateSortOrder(id, sortOrder);
        return collectionMapper.findById(id);
    }

    public void delete(long id) {
        requireExists(id);
        collectionMapper.deleteArtworkLinksByCollection(id);
        collectionMapper.deleteById(id);
        iconService.deleteAll(id);
        log.info(message("collection.log.deleted", id));
    }

    public Collection setIcon(long id, String originalFilename, byte[] data) {
        requireExists(id);
        try {
            String ext = iconService.saveIcon(id, originalFilename, data);
            collectionMapper.updateIconExt(id, ext);
        } catch (java.io.IOException e) {
            throw new LocalizedException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "collection.icon.save.failed",
                    "保存图标失败: {0}",
                    e.getMessage()
            );
        }
        return collectionMapper.findById(id);
    }

    public Collection clearIcon(long id) {
        requireExists(id);
        iconService.deleteAll(id);
        collectionMapper.updateIconExt(id, null);
        return collectionMapper.findById(id);
    }

    public boolean addArtwork(long collectionId, long artworkId) {
        requireExists(collectionId);
        int changed = collectionMapper.addArtwork(collectionId, artworkId, Instant.now().getEpochSecond());
        return changed > 0;
    }

    public boolean removeArtwork(long collectionId, long artworkId) {
        requireExists(collectionId);
        return collectionMapper.removeArtwork(collectionId, artworkId) > 0;
    }

    public void removeArtworkFromAll(long artworkId) {
        collectionMapper.removeAllArtworkLinks(artworkId);
    }

    public List<Long> collectionsOf(long artworkId) {
        return collectionMapper.findCollectionIdsByArtwork(artworkId);
    }

    public Map<Long, List<Long>> membershipsOf(List<Long> artworkIds) {
        Map<Long, List<Long>> result = new HashMap<>();
        if (artworkIds == null || artworkIds.isEmpty()) return result;
        for (Map<String, Object> row : collectionMapper.findLinksByArtworks(artworkIds)) {
            Long artworkId = ((Number) row.get("artworkId")).longValue();
            Long collectionId = ((Number) row.get("collectionId")).longValue();
            result.computeIfAbsent(artworkId, k -> new ArrayList<>()).add(collectionId);
        }
        return result;
    }

    public List<Long> artworkIdsInCollections(Set<Long> collectionIds) {
        if (collectionIds == null || collectionIds.isEmpty()) {
            return List.of();
        }
        return collectionMapper.findArtworkIdsInCollections(collectionIds);
    }

    private void requireExists(long id) {
        if (!exists(id)) {
            throw LocalizedException.badRequest("collection.not-found", "收藏夹不存在: {0}", id);
        }
    }

    private String validateName(String name) {
        if (!StringUtils.hasText(name)) {
            throw LocalizedException.badRequest(
                    "validation.collection.name.required",
                    "收藏夹名称不能为空"
            );
        }
        String clean = name.trim();
        if (clean.length() > MAX_NAME_LENGTH) {
            throw LocalizedException.badRequest(
                    "collection.name.too-long",
                    "收藏夹名称过长（最多 {0} 字符）",
                    MAX_NAME_LENGTH
            );
        }
        return clean;
    }

    private String message(String code, Object... args) {
        return messages.getForLog(code, args);
    }
}
