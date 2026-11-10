package org.example.dao;

import org.example.dao.model.DeviceSession;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class DeviceSessionDao {

    private final Map<String, DeviceSession> sessions = new ConcurrentHashMap<>();

    public void save(DeviceSession session) {
        sessions.put(session.getId(), session);
    }

    public DeviceSession get(String id) {
        return sessions.get(id);
    }

    public void remove(String id) {
        sessions.remove(id);
    }

    public Collection<DeviceSession> all() {
        return sessions.values();
    }
}
