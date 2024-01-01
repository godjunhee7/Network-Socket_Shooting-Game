import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.Socket;
import java.util.List;
import java.util.ArrayList;
import java.util.Queue;
import java.util.LinkedList;
import java.util.Iterator;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import java.io.File;

public class Game_View2 extends JPanel implements ActionListener, KeyListener {
	// 게임의 주요 이미지 및 상태 변수
	private Timer timer; // 게임 업데이트를 위한 타이머
	private Image bulletImage;
	private Image myFlightImage;
	private Image otherFlightImage;
	private Image backgroundImage;
	private Image explosionImage;
	private Image bexplosionImage;
	private Image WinImage;
	private Image LoseImage;
	private Image ultimateImage;
	private List<Rectangle> myUltimates;  // 궁극기 발사
	private boolean isUltimateFired; // 궁극기 발사 상태 추적 변수
	private boolean isUltimateFlipped; // 궁극기 반전 상태 추적 변수
	
	private boolean GameStarted = false; // 상대 클라이언트가 들어오면 시작하기 위함
	
	private final int PlayerSPEED = 7; // 플레이어 이동 속도
	private final int BulletSPEED = 13;  // 총알 속도

	private boolean myFlipped; // 내 이미지가 좌우 반전 된 상태를 유지하기 위함.
	private boolean otherFlipped; // 상대 이미지가 좌우 반전 된 상태를 유지하기 위함.
	private boolean isHit = false; // 충돌 상태 추적
	private boolean myBulletFlippedState = false; // 총알이 우 -> 좌로 발사 되는 순간의 정보를 상대 클라이언트에 보내기 위한 flag 변수

	// 플레이어 및 상대 위치, 총알 관련 변수
	private int myX, myY;
	private int otherX, otherY;
	private boolean[] myKeys; // 내 키 입력 상태 배열
	private boolean[] otherKeys; // 상대 키 입력 상태 배열
	private int otherBulletX, otherBulletY;
	
	
	private boolean otherBulletFlippedState = false;

	private List<Rectangle> otherBullets;
	private List<Rectangle> otherBulletsFlipped;
	
	private List<Rectangle> myBullets;  // 좌 -> 우로 총알 발사
	private List<Rectangle> myBulletsFlipped;   // 우 -> 좌로 총알 발사

	private boolean isBulletFired; // 총알 발사 상태 추적 변수
	private boolean gameOver = false; // 게임 종료 상태 추적 변수

	private Socket socket;
	private PrintWriter out;
	private BufferedReader in;
	
    private int myHP = 100; // 내 HP
    private int otherHP = 100; // 상대방 HP
    private long explosionStartTime = -1;  //폭발 시작 시간
    
    // 화면 해상도 구하기
    private Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
    private int screenWidth = screenSize.width;
    private int screenHeight = screenSize.height;

	public Game_View2() {
		bulletImage = new ImageIcon("./image/enemyBullet.png").getImage();
		myFlightImage = new ImageIcon("./image/F35K.png").getImage();
		otherFlightImage = new ImageIcon("./image/F4K.png").getImage();
		backgroundImage = new ImageIcon("./image/background.png").getImage(); // 배경 이미지 로드
		explosionImage = new ImageIcon("./image/explosion.png").getImage();
		bexplosionImage = new ImageIcon("./image/Bexplosion.png").getImage();
		WinImage = new ImageIcon("./image/Win2.png").getImage();
		LoseImage = new ImageIcon("./image/Lose2.png").getImage();
		ultimateImage = new ImageIcon("./image/explosion1.png").getImage();
		
		myUltimates = new ArrayList<>();
	    isUltimateFired = false;
	    isUltimateFlipped = false; // 초기 반전 상태는 false로 설정

		this.myX = 100;
		this.myY = 200;
		this.myFlipped = false; // 오른쪽을 향하도록 설정
		
		this.otherX = 700;
		this.otherY = 200;
		this.otherFlipped = true; // 왼쪽을 향하도록 설정
		
		myBullets = new ArrayList<>();
		myBulletsFlipped = new ArrayList<>();
		otherBullets = new ArrayList<>();
		otherBulletsFlipped = new ArrayList<>();
		isBulletFired = false;

		myKeys = new boolean[256];
		otherKeys = new boolean[256];

		// 네트워크 설정
		setupNetwork();
		// 프레임의 대한 설정.
		timer = new Timer(15, this);
		timer.start();
		addKeyListener(this);
		setFocusable(true);
		setPreferredSize(new Dimension(854, 400));
	}

	// Server 연결
	private void setupNetwork() {
		try {
			socket = new Socket("localhost", 30000);
			out = new PrintWriter(socket.getOutputStream(), true);
			in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			new Thread(() -> listenForServerMessages()).start(); // 서버로 부터 받을 메시지 thread 시작
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void listenForServerMessages() {  // 서버 메시지 수신 대기 및 처리
		try {
	           String message;
	           while ((message = in.readLine()) != null) {
		            	if (message.equals("Waiting for another player...")) {
		            		System.out.println(message);
		                } 
		            	else if (message.equals("Game starts now!")) {
		            		GameStarted = true;
		            		myX = 100;
		            		myY = 200;
		                    SwingUtilities.invokeLater(this::createAndShowGameFrame);
		                    SoundMusic.playMusic("./music/backgroundMusic.wav");
		            	}
		            	else if(GameStarted) {
		                    String[] parts = message.split(":");

		                    if (parts.length == 2) {
		                        int clientId = Integer.parseInt(parts[0].trim());
		                        String[] coordinates = parts[1].trim().split(",");
		                        // 상대 클라이언트 위치 정보 처리
		                        otherX = Integer.parseInt(coordinates[0].trim());
		                        otherY = Integer.parseInt(coordinates[1].trim());
		                        
		                        // 상대 클라이언트 총알 정보 처리
		                        otherBulletX = Integer.parseInt(coordinates[2].trim());
		                        otherBulletY = Integer.parseInt(coordinates[3].trim());
		                        
		                        // 상대 클라이언트가 방향(좌우)정보 처리
		                        otherFlipped = Boolean.parseBoolean(coordinates[4].trim());
		                        otherBulletFlippedState = Boolean.parseBoolean(coordinates[5].trim());
		                        
		                        
		                        if (otherBulletX != -1 && otherBulletY != -1) {
		                           if (otherBulletFlippedState) {  // 좌 -> 우로 발사된 상대방의 총알 좌표
		                              otherBullets.add(new Rectangle(otherBulletX, otherBulletY, bulletImage.getWidth(null), bulletImage.getHeight(null)));
		                              
		                           }
		                           else {  // 우 -> 좌로 발사된 상대방의 총알 좌표
		                              otherBulletsFlipped.add(new Rectangle(otherBulletX, otherBulletY, bulletImage.getWidth(null), bulletImage.getHeight(null)));
		                           
		                           }
		                        }
		                    }
		            	}
	               }
	       } catch (IOException e) {
	           e.printStackTrace();
	       }

	}

	 // 서버에 플레이어 위치 및 상태 전송
	private void sendPositionToServer(int myX, int myY, int myBulletX, int myBulletY, boolean flipped, boolean bulletFlipped) {
		
		if (out != null) {
			out.println(myX + ", " + myY + ", " + myBulletX + ", " + myBulletY + ", " + flipped + ", " + bulletFlipped);
		}
	}

	@Override
	public void paintComponent(Graphics g) {
		// 게임 화면 그리기 (비행기, 총알, 폭발 등)
		super.paintComponent(g);
		g.clearRect(0, 0, 854, 400);
		g.drawImage(backgroundImage, 0, 0, this.getWidth(), this.getHeight(), this);

        if(!gameOver) {   // 게임이 진행 중인 경우
	         // 내 비행기의 왼쪽 방향키가 눌린 경우 이미지를 좌우 반전
	         if (myFlipped) {
	             g.drawImage(myFlightImage, myX + myFlightImage.getWidth(this), myY, -myFlightImage.getWidth(this), myFlightImage.getHeight(this), this);
	         } else {
	             g.drawImage(myFlightImage, myX, myY, this);
	         }
	         
	         // 상대 비행기의 왼쪽 방향키가 눌린 경우 이미지를 좌우 반전
	         if (otherFlipped) {
	        	 g.drawImage(otherFlightImage, otherX + otherFlightImage.getWidth(this), otherY, -otherFlightImage.getWidth(this), otherFlightImage.getHeight(this), this);
	         }else {
	        	 g.drawImage(otherFlightImage, otherX, otherY,this);
	         }
	         
	         // 궁극기 그리기
	         for(Rectangle ultimate : myUltimates) {
	             if(isUltimateFlipped) {
	                 g.drawImage(ultimateImage, ultimate.x + ultimateImage.getWidth(this), ultimate.y, -ultimateImage.getWidth(this), ultimateImage.getHeight(this), this);
	             } else {
	                 g.drawImage(ultimateImage, ultimate.x, ultimate.y, this);
	             }
	         }
	         
	      // 내 총알 그리기 (좌 -> 우)
	         for(Rectangle bullet : new ArrayList<>(myBullets)) {
	        	 g.drawImage(bulletImage, bullet.x, bullet.y, this);
	        	 
	         }
	         // 내 총알 그리기 (우 -> 좌)
	         for (Rectangle bullet : new ArrayList<>(myBulletsFlipped)) {
	 			g.drawImage(bulletImage, bullet.x, bullet.y, this);
	 			
	 		}
	         
	         // 상대 총알 그리기
	         for(Rectangle bullet : new ArrayList<>(otherBullets)) {
	        	 g.drawImage(bulletImage, bullet.x, bullet.y, this);
	        	 
	         }
	         for(Rectangle bullet : new ArrayList<>(otherBulletsFlipped)) {
	        	 g.drawImage(bulletImage, bullet.x, bullet.y, this);
	        	 
	         }
	         
	         // 폭발 그리기
	         Iterator<Explosion> explosionIterator = explosions.iterator();
	         while (explosionIterator.hasNext()) {
	             Explosion explosion = explosionIterator.next();
	             if (explosion.isExpired()) {
	                 explosionIterator.remove(); // 시간이 지난 폭발 제거
	             } else {
	                 g.drawImage(explosionImage, explosion.location.x, explosion.location.y, this);
	             }
	         }
	     
	       	     Graphics2D g2d = (Graphics2D) g; // Graphics 객체를 Graphics2D로 캐스팅

	       	    // 텍스트에 사용할 폰트 설정
	       	    Font font = new Font("Serif", Font.BOLD, 18);
	       	    g2d.setFont(font);

	       	    // 텍스트 색상을 오렌지색으로 설정
	       	    g2d.setColor(Color.ORANGE);
	       	    g2d.drawString("My HP: " + myHP, 10, 35);   // 10, 35는 화면 좌측 상단에 표시하기 위함

	       	    // 텍스트 색상을 빨간색으로 변경
	       	    g2d.setColor(Color.RED);
	       	    g2d.drawString("Enemy HP: " + otherHP, getWidth() - 140, 35);   // - 140, 35는 화면 우측 상단에 표시하기 위함

        }
        
        if (gameOver) {  // 게임이 종료된 경우
            long currentTime = System.currentTimeMillis();
            
            
                if (myHP <= 0) {	// 본인 비행기가 패배한 경우
                	myHP = 0;
                	if (currentTime - explosionStartTime <= 3000)  {  // 3초동안 본인 비행기 폭발 및 움직임 가능
	                    g.drawImage(bexplosionImage, myX-30, myY-40, this);   // -30 -40은 비행기와 폭발이 겹치도록 하기 위함
	                    
	 	                   
		       	         if (myFlipped) {
		    	             g.drawImage(myFlightImage, myX + myFlightImage.getWidth(this), myY, -myFlightImage.getWidth(this), myFlightImage.getHeight(this), this);
		    	         } else {
		    	             g.drawImage(myFlightImage, myX, myY, this);
		    	         }
	                    
		    	         // 상대 비행기의 왼쪽 방향키가 눌린 경우 이미지를 좌우 반전
		    	         if (otherFlipped) {
		    	        	 g.drawImage(otherFlightImage, otherX + otherFlightImage.getWidth(this), otherY, -otherFlightImage.getWidth(this), otherFlightImage.getHeight(this), this);
		    	         }else {
		    	        	 g.drawImage(otherFlightImage, otherX, otherY,this);
		    	         }
	                    
		    	         
		    	         
	 		       	     Graphics2D g2d = (Graphics2D) g; // Graphics 객체를 Graphics2D로 캐스팅

	  		       	    // 텍스트에 사용할 폰트 설정
	  		       	    Font font = new Font("Serif", Font.BOLD, 18);
	  		       	    g2d.setFont(font);

	  		       	    // 텍스트 색상을 오렌지색으로 설정
	  		       	    g2d.setColor(Color.ORANGE);
	  		       	    g2d.drawString("My HP: " + myHP, 10, 35);	// 10, 35는 화면 좌측 상단에 표시하기 위함

	  		       	    // 텍스트 색상을 빨간색으로 변경
	  		       	    g2d.setColor(Color.RED);
	  		       	    g2d.drawString("Enemy HP: " + otherHP, getWidth() - 140, 35);    // - 140, 35는 화면 우측 상단에 표시하기 위함
                	}
                	
                	else if(currentTime - explosionStartTime <= 7000) {		// (7 - 3) = 4초 동안 이긴 자의 자유시간
		    	         if (otherFlipped) {
		    	        	 g.drawImage(otherFlightImage, otherX + otherFlightImage.getWidth(this), otherY, -otherFlightImage.getWidth(this), otherFlightImage.getHeight(this), this);
		    	         }else {
		    	        	 g.drawImage(otherFlightImage, otherX, otherY,this);
		    	         }
                		
                	}
                	
                 	else {     // 4초 자유시간이 지난 이후
                 	// 이미지를 화면 중앙에 위치시키기 위한 좌표 계산
                 	    int x = getWidth() / 2 - LoseImage.getWidth(null) / 2;
                 	    int y = getHeight() / 2 - LoseImage.getHeight(null) / 2;

                 	    // 10초동안 이미지 그리기
                 	    g.drawImage(LoseImage, x, y, this);

                 	    // 10초 후 타이머로 프로그램 종료
                 	    Timer exitTimer = new Timer(10000, new ActionListener() {
                 	        public void actionPerformed(ActionEvent e) {
                 	            System.exit(0); // 프로그램 종료
                 	        }
                 	    });
                 	    exitTimer.setRepeats(false); // 타이머가 한 번만 실행되도록 설정
                 	    exitTimer.start(); // 타이머 시작
  
  
                 	}
                	
                }
                
                else if (otherHP <= 0) {	// 상대 비행기가 패배한 경우
                	otherHP = 0;
                	if (currentTime - explosionStartTime <= 3000)  {   // 3초동안 상대 비행기 폭발 및 움직임 가능
	                    g.drawImage(bexplosionImage, otherX-30, otherY-40, this);  // -30 -40은 비행기와 폭발이 겹치도록 하기 위함

	                    
		    	         if (otherFlipped) {
		    	        	 g.drawImage(otherFlightImage, otherX + otherFlightImage.getWidth(this), otherY, -otherFlightImage.getWidth(this), otherFlightImage.getHeight(this), this);
		    	         }else {
		    	        	 g.drawImage(otherFlightImage, otherX, otherY,this);
		    	         }
	                    
		       	         if (myFlipped) {
		    	             g.drawImage(myFlightImage, myX + myFlightImage.getWidth(this), myY, -myFlightImage.getWidth(this), myFlightImage.getHeight(this), this);
		    	         } else {
		    	             g.drawImage(myFlightImage, myX, myY, this);
		    	         }
		    	         		
		       	      
		       	         
	 		       	     Graphics2D g2d = (Graphics2D) g; // Graphics 객체를 Graphics2D로 캐스팅

	  		       	    // 텍스트에 사용할 폰트 설정
	  		       	    Font font = new Font("Serif", Font.BOLD, 18);
	  		       	    g2d.setFont(font);

	  		       	    // 텍스트 색상을 오렌지색으로 설정
	  		       	    g2d.setColor(Color.ORANGE);
	  		       	    g2d.drawString("My HP: " + myHP, 10, 35);	// 10, 35는 화면 좌측 상단에 표시하기 위함

	  		       	    // 텍스트 색상을 빨간색으로 변경
	  		       	    g2d.setColor(Color.RED);
	  		       	    g2d.drawString("Enemy HP: " + otherHP, getWidth() - 140, 35);    // - 140, 35는 화면 우측 상단에 표시하기 위함
                	}
                	
                	else if(currentTime - explosionStartTime <= 7000) {		// (7 - 3) = 4초 동안 이긴 자의 자유시간
		       	         if (myFlipped) {
		    	             g.drawImage(myFlightImage, myX + myFlightImage.getWidth(this), myY, -myFlightImage.getWidth(this), myFlightImage.getHeight(this), this);
		    	         } else {
		    	             g.drawImage(myFlightImage, myX, myY, this);
		    	         }
                	}
                	
                 	else {     // 4초 자유시간 지난 후  

                 		// 이미지를 화면 중앙에 위치시키기 위한 좌표 계산
                 	    int x = getWidth() / 2 - WinImage.getWidth(null) / 2;
                 	    int y = getHeight() / 2 - WinImage.getHeight(null) / 2;

                 	    // 10초동안 이미지 그리기
                 	    g.drawImage(WinImage, x, y, this);

                 	    // 10초 후 타이머로 프로그램 종료
                 	    Timer exitTimer = new Timer(10000, new ActionListener() {
                 	        public void actionPerformed(ActionEvent e) {
                 	            System.exit(0); // 프로그램 종료
                 	        }
                 	    });
                 	    exitTimer.setRepeats(false); // 타이머가 한 번만 실행되도록 설정
                 	    exitTimer.start(); 
                  	  
                 		
                 	}
                	
                }      
            
        }

	}
	
	
	public void actionPerformed(ActionEvent e) {
		updatePlayerPosition();
		updateMyBullets();
		updateOtherBullets();
		updateMyUltimate();
		checkExplosions();
		repaint();
	}
	
	private void updateMyUltimate() {
	    // 궁극기 위치 업데이트
	    for (Iterator<Rectangle> it = myUltimates.iterator(); it.hasNext();) {
	        Rectangle ultimate = it.next();
	        ultimate.x += BulletSPEED; // 궁극기도 총알처럼 BulletSPEED로 움직입니다.
	        if (ultimate.x > getWidth()) {
	            it.remove(); // 화면 밖으로 나가면 제거
	        }
	    }
	}
	

	// 플레이어 위치 업데이트
	private void updatePlayerPosition() {
		if (myKeys[KeyEvent.VK_W]) {
			myY -= PlayerSPEED; // W 키가 눌리면 위로 이동
		}
		if (myKeys[KeyEvent.VK_S]) {
			myY += PlayerSPEED; // S 키가 눌리면 아래로 이동
		}
		if (myKeys[KeyEvent.VK_A]) {
			myX -= PlayerSPEED; // A 키가 눌리면 왼쪽으로 이동
			myFlipped = true;
		}
		if (myKeys[KeyEvent.VK_D]) {
			myX += PlayerSPEED; // D 키가 눌리면 오른쪽으로 이동
			myFlipped = false;
		}
		sendPositionToServer(myX, myY, -1, -1, myFlipped, myBulletFlippedState);
		myX = Math.max(myX, 0);
		myX = Math.min(myX, getWidth() - myFlightImage.getWidth(null));
		myY = Math.max(myY, 0);
		myY = Math.min(myY, getHeight() - myFlightImage.getHeight(null));

	}

	// 총알 발사
	public void keyPressed(KeyEvent e) {
		myKeys[e.getKeyCode()] = true;
		if (e.getKeyCode() == KeyEvent.VK_SPACE && !isBulletFired) {
			// 총알 발사: 플레이어 위치에서 시작하는 새 총알을 추가합니다.
			if (!myFlipped) {
				myBulletFlippedState = true;
				myBullets.add(new Rectangle(myX + myFlightImage.getWidth(null) / 2 - bulletImage.getWidth(null) / 2,
						myY, myFlightImage.getWidth(null), bulletImage.getHeight(null)));
			} else {
				myBulletFlippedState = false;
				myBulletsFlipped.add(new Rectangle(myX - bulletImage.getWidth(null),
						myY + myFlightImage.getHeight(null) / 2 - bulletImage.getHeight(null) / 2,
						bulletImage.getWidth(null), bulletImage.getHeight(null)));
			}
			isBulletFired = true;
			SoundMusic.playMusic("./music/missileSound.wav");
		}
		
		if(e.getKeyCode() == KeyEvent.VK_R && !isUltimateFired) {
			isUltimateFlipped = myFlipped; // 현재 비행기의 반전 상태에 따라 궁극기 반전 상태를 결정
	        Rectangle newUltimate = new Rectangle(myX + myFlightImage.getWidth(null) / 2 - ultimateImage.getWidth(null) / 2,
	                myY, ultimateImage.getWidth(null), ultimateImage.getHeight(null));
	        myUltimates.add(newUltimate);
	        isUltimateFired = true;
		}

	}

	// 발사된 총알 위치 추적
	private void updateMyBullets() {
		boolean hasBullets = !myBullets.isEmpty();
		boolean hasBullets2 = !myBulletsFlipped.isEmpty();

		for (Iterator<Rectangle> it = myBullets.iterator(); it.hasNext();) {
			Rectangle myBullets = it.next();
			myBullets.x += BulletSPEED;
			if (myBullets.x > 855) {
				it.remove();
			}
		}

		for (Iterator<Rectangle> it = myBulletsFlipped.iterator(); it.hasNext();) {
			Rectangle myBulletsFlipped = it.next();
			myBulletsFlipped.x -= BulletSPEED;
			if (myBulletsFlipped.x < 0) {
				it.remove();
			}
		}

	}

	private void updateOtherBullets() {
		for (Iterator<Rectangle> it = otherBullets.iterator(); it.hasNext();) {
			Rectangle bullet = it.next();
			bullet.x += (BulletSPEED); // 총알의 x 좌표 업데이트
			
			if (bullet.x > 855) { // 화면 밖으로 나가면 제거
				it.remove();
			}
		}
		for (Iterator<Rectangle> it = otherBulletsFlipped.iterator(); it.hasNext();) {
  	        Rectangle bullet = it.next();
  	        
  	        bullet.x -= (BulletSPEED); // 총알의 x 좌표 업데이트
  	        if (bullet.x < 0) { // 화면 밖으로 나가면 제거
  	            it.remove();
  	        }
  	   }
	}

	public void keyReleased(KeyEvent e) {
		myKeys[e.getKeyCode()] = false;
		if (e.getKeyCode() == KeyEvent.VK_SPACE) {
			sendPositionToServer(myX, myY, myX, myY, myFlipped, myBulletFlippedState);
			
			isBulletFired = false;
		}
		if (e.getKeyCode() == KeyEvent.VK_R) {
	        isUltimateFired = false;
	    }

	}
	// 0.1초 동안 폭발이 일어난 위치에 explosion 처리
    private class Explosion{
    	Point location;
    	 long startTime;

         public Explosion(Point location) {
             this.location = location;
             this.startTime = System.currentTimeMillis();
         }

         public boolean isExpired() {
             return System.currentTimeMillis() - startTime > 100; // 0.1초가 지났는지 확인
         }
    }
    private List<Explosion> explosions = new ArrayList<>();
	// 폭발 처리(내 위치와 상대 총알 위치가 일치)
    private void checkExplosions() {
    	Rectangle myPlaneRect = new Rectangle(myX, myY, myFlightImage.getWidth(null), myFlightImage.getHeight(null));
    	Rectangle otherPlaneRect = new Rectangle(otherX, otherY, otherFlightImage.getWidth(null), otherFlightImage.getHeight(null));

   	 	// 상대가 쏜 총알에 내 비행기가 맞았을 때 action
    	 for (Iterator<Rectangle> it = otherBullets.iterator(); it.hasNext();) {
    	        Rectangle bullet = it.next();
    	        if (myPlaneRect.intersects(bullet)) {
    	            it.remove(); // 충돌한 총알 제거
    	            
    	            // 충돌 x 좌표에 +15, -15한 이유는 비행기 몸통에서 충돌 이미지가 나타나기 위함.
    	            boolean check = explosions.add(new Explosion(new Point(bullet.x+15, bullet.y))); // 총알 맞은 해당 위치에 폭발
                    if(check) {
                    	myHP -= 5; // HP 감소
                    	
                    	if(myHP <= 0) {
                    		myHP = 0;
                    	}
                    } 
    	            isHit = true;
    	          
    	            checkGameOver();
    	            break;
    	        }

    	    }

        
    	  for (Iterator<Rectangle> it = otherBulletsFlipped.iterator(); it.hasNext();) {
    	        Rectangle bullet = it.next();
    	        if (myPlaneRect.intersects(bullet)) {
    	            
    	            it.remove(); // 충돌한 총알 제거
    	            boolean check = explosions.add(new Explosion(new Point(bullet.x-15, bullet.y))); // 총알 맞은 해당 위치에 폭발
                    if(check) {
                    	myHP -= 5; // HP 감소
                    	if(myHP <= 0) {
                    		myHP = 0;
                    	}
                    } 

    	            checkGameOver();
    	            break;
    	        }
    	    }
    	  // 내가 쏜 총알에 상대 비행기가 맞았을 때 action
    	  for (Iterator<Rectangle> it = myBullets.iterator(); it.hasNext();) {
  	        Rectangle bullet = it.next();
  	        if (otherPlaneRect.intersects(bullet)) {

  	            it.remove(); // 상대방에게 맞은 총알 제거	            
  	            boolean check = explosions.add(new Explosion(new Point(bullet.x+15, bullet.y))); // 총알 맞은 해당 위치에 폭발
  	            if(check) {
  	            	otherHP -= 5; // 상대 HP 감소
  	            	if(otherHP <= 0) otherHP = 0;
  	            } 
  	            
  	            isHit = true;
  	            
  	            checkGameOver();
  	            break;
  	        }

  	    }

  	    for (Iterator<Rectangle> it = myBulletsFlipped.iterator(); it.hasNext();) {
  	        Rectangle bullet = it.next();
  	        if (otherPlaneRect.intersects(bullet)) {
  	            
  	            it.remove(); // 상대방에게 맞은 총알 제거
  	            boolean check = explosions.add(new Explosion(new Point(bullet.x-15, bullet.y))); // 총알 맞은 해당 위치에 폭발
  	            if(check) {
  	            	otherHP -= 5; // 상대 HP 감소
  	            	if(otherHP <= 0) otherHP = 0;
  	            } 
  	            checkGameOver();
  	            break;
  	        }
  	    }
    }
    public void createAndShowGameFrame() {   // 게임 창 생성 및 위치 설정
        JFrame frame = new JFrame("Client2");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.add(this);
        frame.pack();
        frame.setVisible(true);
        frame.setLocation(screenWidth - frame.getWidth() , screenHeight - frame.getHeight());
        //화면 우측 하단에 표시 
        this.requestFocusInWindow();
    }
    
    private void checkGameOver() {  // 게임이 종료되었는 지, 검사하는 함수
   	 if (myHP <= 0 || otherHP <= 0) {
	        gameOver = true;
	       	        
	        if (explosionStartTime == -1) {
	            explosionStartTime = System.currentTimeMillis(); // 폭발 시작 시간을 기록합니다.
	            
	        }

	 }
   	 repaint();
    }

	public void keyTyped(KeyEvent e) {
	}

	public static void main(String[] args) {
		Game_View2 gamePanel = new Game_View2();
	}
}
