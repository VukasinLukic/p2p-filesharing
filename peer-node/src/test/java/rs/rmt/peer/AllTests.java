package rs.rmt.peer;

import rs.rmt.peer.api.PeerApiServerTest;
import rs.rmt.peer.share.ChunkHasherTest;
import rs.rmt.peer.share.SharedFolderScannerTest;
import rs.rmt.peer.testutil.TestRunner;
import rs.rmt.peer.transfer.ChunkManifestTransferTest;
import rs.rmt.peer.transfer.DownloadManagerTest;
import rs.rmt.peer.transfer.DownloadServiceTest;
import rs.rmt.peer.transfer.MultiSourceDownloadTest;
import rs.rmt.peer.transfer.TransferProtocolTest;
import rs.rmt.peer.util.JsonTest;
import rs.rmt.peer.util.LocalAddressTest;

public final class AllTests {
    public static void main(String[] args) {
        TestRunner.run(
                JsonTest.class,
                LocalAddressTest.class,
                SharedFolderScannerTest.class,
                ChunkHasherTest.class,
                TransferProtocolTest.class,
                DownloadManagerTest.class,
                DownloadServiceTest.class,
                MultiSourceDownloadTest.class,
                ChunkManifestTransferTest.class,
                PeerApiServerTest.class
        );
    }
}
