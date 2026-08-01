package rs.rmt.peer.share;

import rs.rmt.peer.model.FileMeta;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory catalog of files this peer currently offers to the network: fileHash -> local Path. */
public final class LibraryService {
    private final Map<String, Path> pathsByHash = new ConcurrentHashMap<>();
    private final Map<String, FileMeta> metaByHash = new ConcurrentHashMap<>();

    public void replaceAll(List<SharedFolderScanner.ScannedFile> files) {
        Map<String, Path> newPaths = new ConcurrentHashMap<>();
        Map<String, FileMeta> newMeta = new ConcurrentHashMap<>();
        for (SharedFolderScanner.ScannedFile f : files) {
            newPaths.put(f.fileHash(), f.path());
            newMeta.put(f.fileHash(), new FileMeta(f.fileHash(), f.fileName(), f.size()));
        }
        pathsByHash.clear();
        pathsByHash.putAll(newPaths);
        metaByHash.clear();
        metaByHash.putAll(newMeta);
    }

    public void addFile(String fileHash, String fileName, long size, Path path) {
        pathsByHash.put(fileHash, path);
        metaByHash.put(fileHash, new FileMeta(fileHash, fileName, size));
    }

    public Optional<Path> resolve(String fileHash) {
        return Optional.ofNullable(pathsByHash.get(fileHash));
    }

    public boolean contains(String fileHash) {
        return pathsByHash.containsKey(fileHash);
    }

    public List<FileMeta> allFiles() {
        return new ArrayList<>(metaByHash.values());
    }
}
