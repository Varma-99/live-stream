package com.livestream.cluster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.livestream.LiveStreamConfiguration;
import com.livestream.config.PeerInstance;
import java.util.List;
import org.junit.jupiter.api.Test;

class PeerInstanceClientTest {

    @Test
    void findPeer_returnsConfiguredInstance() {
        LiveStreamConfiguration config = new LiveStreamConfiguration();
        PeerInstance peer = new PeerInstance();
        peer.setId("app-2");
        peer.setHost("http://127.0.0.1:8092");
        config.setPeerInstances(List.of(peer));

        PeerInstanceClient client = new PeerInstanceClient(config, new ObjectMapper());
        assertTrue(client.findPeer("app-2").isPresent());
        assertEquals("app-2", client.findPeer("app-2").orElseThrow().getId());
    }

    @Test
    void findPeer_emptyForUnknownId() {
        LiveStreamConfiguration config = new LiveStreamConfiguration();
        config.setPeerInstances(List.of());

        PeerInstanceClient client = new PeerInstanceClient(config, new ObjectMapper());
        assertTrue(client.findPeer("app-99").isEmpty());
    }
}
