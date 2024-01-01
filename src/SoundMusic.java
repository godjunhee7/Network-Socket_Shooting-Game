import javax.sound.sampled.*;
import java.io.File;

public class SoundMusic {

    public static void playMusic(String filePath) {
        try {
            File musicPath = new File(filePath);
            if (musicPath.exists()) {
                AudioInputStream audioInput = AudioSystem.getAudioInputStream(musicPath);
                Clip clip = AudioSystem.getClip();
                clip.open(audioInput);
                
                // 사운드 재생 완료 이벤트 리스너 추가
                clip.addLineListener(event -> {
                    if (event.getType() == LineEvent.Type.STOP) {
                        clip.close();
                    }
                });

                clip.start();
                
            } else {
                System.out.println("음악 파일을 찾을 수 없습니다.");
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

}
