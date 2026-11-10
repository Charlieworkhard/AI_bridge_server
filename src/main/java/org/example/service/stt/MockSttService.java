package org.example.service.stt;

import org.example.config.AppProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "app.stt", name = "provider", havingValue = "mock")
public class MockSttService implements SttService {

    private final AppProperties props;

    public MockSttService(AppProperties props) {
        this.props = props;
    }

    @Override
    public String transcribe(short[] pcm16k) {
        return props.getStt().getMockText();
    }
}
