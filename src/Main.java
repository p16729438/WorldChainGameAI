import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;
import java.util.Scanner;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class Main
{
	static final String key = YOUR_KEY; //국립국어원 API Key
	static int lastCode = 535000; //개발 일자 기준 533600번째쯤에 마지막 번호
	static int threadI = 0;
	static final int threadCount = 128; //128스레드 기준 크롤링하는데 걸리는 시간 약 30분
	
	static HashMap<String, Integer> wordMap = new HashMap<String, Integer>(); //시작글자+끝글자의 개수
	static ArrayList<String> usedWord = new ArrayList<String>(); //사용된 단어 목록
	
	static ArrayList<String> oneShotWord = new ArrayList<String>(); //한방단어 목록
	
	static String nowWord = ""; //방금 턴의 마지막 글자
	
	static boolean turn = false; //false = AI, true = 당신
	
	static String word = "";
	static double rate = 0;
	
	public static void main(String[] args)
	{
		File dataFolder = new File("WCGAIdata");
		if((! dataFolder.exists()) || dataFolder.listFiles().length == 0)
		{
			System.out.println("단어 데이터가 존재하지 않아 국립국어원으로부터 데이터를 불러옵니다.");
			downloadData();
		}
		init();
		startGame();
	}
	
	public static void startGame() //게임 시작
	{
		for(int i = 0; i < 30; i++)
		{
			System.out.println(" ");
		}
		System.out.println("AI와의 끝말잇기를 시작합니다.");
		String startWord = startWord();
		while(oneShotWord.contains(getStartEndChar(startWord)))
		{
			startWord = startWord();
		}
		System.out.println("AI : "+startWord+"   ("+getDefinition(startWord)+")");
		usedWord.add(startWord);
		if(wordMap.get(getStartEndChar(startWord)) == 1)
		{
			wordMap.remove(getStartEndChar(startWord));
		}
		else
		{
			wordMap.put(getStartEndChar(startWord), wordMap.get(getStartEndChar(startWord))-1);
		}
		nowWord = String.valueOf(getStartEndChar(startWord).charAt(1));
		yourTurn();
	}
	
	public static int[] simulate(String simulateWord, HashMap<String, Integer> simulateWordMap, ArrayList<String> simulateUsedWord, boolean isMyTurn) //시뮬레이션
	{
		int[] simulateResult = new int[2];
		simulateResult[0] = 0;
		simulateResult[1] = 0;
		HashMap<String, Integer> tempWordMap = (HashMap<String, Integer>) simulateWordMap.clone();
		ArrayList<String> tempUsedWord = (ArrayList<String>) simulateUsedWord.clone();
		String tempNowWord = String.valueOf(simulateWord.charAt(1));
		boolean b = true;
		
		tempUsedWord.add(randomWord(simulateWord));
		if(tempWordMap.get(simulateWord) == 1)
		{
			tempWordMap.remove(simulateWord);
		}
		else
		{
			tempWordMap.put(simulateWord, tempWordMap.get(simulateWord)-1);
		}
		for(String words : tempWordMap.keySet())
		{
			if(words.startsWith(tempNowWord))
			{
				if(! oneShotWord.contains(getStartEndChar(words)))
				{
					int[] tempSimulateResult = simulate(words, tempWordMap, tempUsedWord, ! isMyTurn);
					simulateResult[0] += tempSimulateResult[0];
					simulateResult[1] += tempSimulateResult[1];
					b = false;
				}
			}
		}
		if(b)
		{
			if(isMyTurn)
			{
				simulateResult[0] += 1;
			}
			simulateResult[1] += 1;
		}
		return simulateResult;
	}
	
	public static void myTurn() //인공지능의 차례
	{
		ArrayList<Thread> threads = new ArrayList<Thread>();
		for(String words : wordMap.keySet())
		{
			if(words.startsWith(nowWord))
			{
				if(! oneShotWord.contains(getStartEndChar(words)))
				{
					String temp = words;
					threads.add(new Thread(new Runnable()
					{
						@Override
						public void run()
						{
							double simulateRate = getRate(simulate(temp, wordMap, usedWord, true));
							if(simulateRate > rate)
							{
								word = temp;
								rate = simulateRate;
							}
						}
					}));
				}
			}
		}
		for(Thread thread : threads)
		{
			thread.start();
		}
		try
		{
			for(Thread thread : threads)
			{
				thread.join();
			}
		}
		catch(InterruptedException exception)
		{
			exception.printStackTrace();
		}
		if(threads.size() == 0)
		{
			System.out.println("당신의 승리입니다.");
			return;
		}
		word = randomWord(word);
		System.out.println("AI : "+word+"   ("+getDefinition(word)+")   (승리 "+rate+"% 확신)");
		usedWord.add(word);
		if(wordMap.get(getStartEndChar(word)) == 1)
		{
			wordMap.remove(getStartEndChar(word));
		}
		else
		{
			wordMap.put(getStartEndChar(word), wordMap.get(getStartEndChar(word))-1);
		}
		nowWord = String.valueOf(getStartEndChar(word).charAt(1));
		word = "";
		rate = 0;
		yourTurn();
	}
	
	public static void yourTurn() //유저의 차례
	{
		if(hasCanUseWord())
		{
			System.out.print("당신 : ");
			turn = true;
			Scanner sc = new Scanner(System.in);
			String word = sc.nextLine();
			if(word.startsWith(nowWord))
			{
				if(! usedWord.contains(word))
				{
					if(isWord(word))
					{
						if(! oneShotWord.contains(getStartEndChar(word)))
						{
							usedWord.add(word);
							if(wordMap.get(getStartEndChar(word)) == 1)
							{
								wordMap.remove(getStartEndChar(word));
							}
							else
							{
								wordMap.put(getStartEndChar(word), wordMap.get(getStartEndChar(word))-1);
							}
							nowWord = String.valueOf(getStartEndChar(word).charAt(1));
							myTurn();
						}
						else
						{
							System.out.println("한방단어는 사용할 수 없습니다.");
							yourTurn();
						}
					}
					else
					{
						System.out.println("없는 단어입니다.");
						yourTurn();
					}
				}
				else
				{
					System.out.println("이미 사용한 단어입니다.");
					yourTurn();
				}
			}
			else
			{
				System.out.println(nowWord+"(으)로 시작하는 단어를 입력해주세요.");
				yourTurn();
			}
		}
		else
		{
			System.out.println("더이상 사용할 수 없는 단어가 없습니다.");
			System.out.println("당신의 패배입니다.");
			return;
		}
	}
	
	public static boolean hasCanUseWord()
	{
		boolean b = false;
		for(String words : wordMap.keySet())
		{
			if(words.startsWith(nowWord))
			{
				if(! usedWord.contains(words))
				{
					if(! oneShotWord.contains(getStartEndChar(words)))
					{
						return true;
					}
				}
			}
		}
		return false;
	}
	
	public static String randomWord(String startEndChar)
	{
		ArrayList<String> words = new ArrayList<String>();
		File dataFolder = new File("WCGAIdata");
		for(File wordFile : dataFolder.listFiles())
		{
			if(wordFile.getName().split("\\.")[0].startsWith(String.valueOf(startEndChar.charAt(0))) && wordFile.getName().split("\\.")[0].endsWith(String.valueOf(startEndChar.charAt(1))))
			{
				if(! usedWord.contains(wordFile.getName().split("\\.")[0]))
				{
					words.add(wordFile.getName().split("\\.")[0]);
				}
			}
		}
		Random r = new Random();
		return words.get(r.nextInt(words.size()));
	}
	
	public static boolean isWord(String word)
	{
		File dataFolder = new File("WCGAIdata");
		for(File wordFile : dataFolder.listFiles())
		{
			if(wordFile.getName().split("\\.")[0].equalsIgnoreCase(word))
			{
				return true;
			}
		}
		return false;
	}
	
	public static String startWord()
	{
		File dataFolder = new File("WCGAIdata");
		int wordCount = dataFolder.listFiles().length;
		Random r = new Random();
		return dataFolder.listFiles()[r.nextInt(wordCount)].getName().split("\\.")[0];
	}
	
	public static String getDefinition(String word)
	{
		File wordFile = new File("WCGAIdata/"+word+".txt");
		try
		{
			BufferedReader r = new BufferedReader(new FileReader(wordFile));
			String definition = r.readLine();
			r.close();
			return definition;
		}
		catch(IOException exception)
		{
			exception.printStackTrace();
		}
		return "";
	}
	
	public static double getRate(int[] simulateResult)
	{
		return (((double) simulateResult[0])/((double) simulateResult[1]))*100;
	}
	
	public static void init()
	{
		File dataFolder = new File("WCGAIdata");
		ArrayList<String> temp = new ArrayList<String>();
		System.out.println("데이터 로드중...");
		long start = System.currentTimeMillis();
		for(File wordFile : dataFolder.listFiles())
		{
			
			if(! wordMap.containsKey(getStartEndChar(wordFile.getName().split("\\.")[0])))
			{
				wordMap.put(getStartEndChar(wordFile.getName().split("\\.")[0]), 1);
			}
			else
			{
				wordMap.put(getStartEndChar(wordFile.getName().split("\\.")[0]), wordMap.get(getStartEndChar(wordFile.getName().split("\\.")[0]))+1);
			}
			if(! temp.contains(String.valueOf(wordFile.getName().split("\\.")[0].charAt(0))))
			{
				temp.add(String.valueOf(wordFile.getName().split("\\.")[0].charAt(0)));
			}
		}
		long end = System.currentTimeMillis();
		System.out.println("데이터를 로드하는데 걸린 시간 : "+((double) ((double) (end-start))/1000)+"sec");
		start = System.currentTimeMillis();
		for(File wordFile : dataFolder.listFiles())
		{
			if(! temp.contains(String.valueOf(wordFile.getName().split("\\.")[0].charAt(wordFile.getName().split("\\.")[0].length()-1))))
			{
				oneShotWord.add(getStartEndChar(wordFile.getName().split("\\.")[0]));
			}
		}
		end = System.currentTimeMillis();
		System.out.println("한방단어를 정리하는데 걸린 시간 : "+((double) ((double) (end-start))/1000)+"sec");
	}
	
	public static void downloadData()
	{
		File dataFolder = new File("WCGAIdata");
		if(! dataFolder.exists())
		{
			dataFolder.mkdir();
		}
		try
		{
			Thread.sleep(3000);
		}
		catch (InterruptedException exception)
		{
			exception.printStackTrace();
		}
		long start = System.currentTimeMillis();
		for(int i = lastCode; i >= 0; i--)
		{
			if(addWordData(i, false) == 1)
			{
				lastCode = i;
				System.out.println("마지막 단어의 번호 : "+i);
				break;
			}
		}
		lastCode += threadCount-(lastCode%threadCount);
		long end = System.currentTimeMillis();
		System.out.println("마지막 단어의 번호를 확인하는데 걸린 시간 : "+((double) ((double) (end-start))/1000)+"sec");
		start = System.currentTimeMillis();
		Thread[] threads = new Thread[threadCount];
		for(threadI = 0; threadI < threadCount; threadI++)
		{
			int temp = threadI;
			threads[threadI] = new Thread(new Runnable()
			{
				@Override
				public void run()
				{
					for(int i = ((lastCode/threadCount)*(temp)); i < ((lastCode/threadCount)*(temp+1)); i++)
					{
						addWordData(i, true);
					}
				}
			});
		}
		for(int i = 0; i < threadCount; i++)
		{
			threads[i].start();
		}
		try
		{
			for(int i = 0; i < threadCount; i++)
			{
				threads[i].join();
			}
		}
		catch(InterruptedException exception)
		{
			exception.printStackTrace();
		}
		end = System.currentTimeMillis();
		System.out.println("데이터를 다운로드하는데 걸린 시간 : "+((double) ((double) (end-start))/1000)+"sec");
	}
	
	public static int addWordData(int i, boolean b)
	{
		String word = "";
		boolean isNoun = false;
		String definition = "";
		try
		{
			String url = "https://stdict.korean.go.kr/api/view.do?key="+key+"&method=target_code&q="+i;
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			Document doc = dBuilder.parse(url);
			doc.getDocumentElement().normalize();
			if(doc.getDocumentElement().getNodeName().equalsIgnoreCase("error"))
			{
				System.out.println(i+"번째 데이터를 불러오는 중 오류가 발생했습니다.");
				return 0;
			}
			NodeList n_list = doc.getElementsByTagName("channel");
			Node node1 = n_list.item(0).getFirstChild();
			while(node1 != null)
			{
				if(node1.getNodeName().equalsIgnoreCase("total"))
				{
					if(! node1.getTextContent().equalsIgnoreCase("1"))
					{
						return 0;
					}
				}
				if(node1.getNodeName().equalsIgnoreCase("item"))
				{
					Node node2 = ((Element) node1).getElementsByTagName("word_info").item(0).getFirstChild();
					while(node2 != null)
					{
						if(node2.getNodeName().equalsIgnoreCase("word"))
						{
							word = node2.getTextContent();
							word = word.replace("-", "");
							word = word.replace("^", "");
							word = word.replace(":", "");
							word = word.replace("ㆍ", "");
							word = word.replace("", "");
						}
						if(node2.getNodeName().equalsIgnoreCase("pos_info"))
						{
							Node node3 = ((Element) node2).getElementsByTagName("pos").item(0).getFirstChild();
							if(node3.getTextContent().equalsIgnoreCase("명사") || node3.getTextContent().equalsIgnoreCase("대명사"))
							{
								isNoun = true;
							}
							Node node4 = ((Element) node2).getElementsByTagName("comm_pattern_info").item(0).getFirstChild();
							while(node4 != null)
							{
								if(node4.getNodeName().equalsIgnoreCase("sense_info"))
								{
									Node node5 = ((Element) node4).getElementsByTagName("definition").item(0).getFirstChild();
									definition = node5.getTextContent();
									if(definition.length() > 16)
									{
										definition = definition.substring(0, 16)+"...";
									}
								}
								node4 = node4.getNextSibling();
							}
						}
						node2 = node2.getNextSibling();
					}
				}
				node1 = node1.getNextSibling();
			}
			if(b)
			{
				if(isNoun)
				{
					if(isComplete(word))
					{
						File wordFile = new File("WCGAIdata/"+word+".txt");
						if(! wordFile.exists())
						{
							wordFile.createNewFile();
							BufferedWriter w = new BufferedWriter(new FileWriter(wordFile));
							w.append(definition);
							w.flush();
							w.close();
							System.out.println("단어 "+word+"을(를) 추가했습니다.");
						}
					}
					else
					{
						return 0;
					}
				}
			}
			
		}
		catch(IOException | ParserConfigurationException | SAXException exception)
		{
			exception.printStackTrace();
			return 0;
		}
		return 1;
	}
	
	public static boolean isComplete(String word)
	{
		boolean b = true;
		if(word.length() < 2)
		{
			return false;
		}
		String not = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ";
		for(int i = 0; i < not.length(); i++)
		{
			if(word.contains(String.valueOf(not.charAt(i))))
			{
				b = false;
			}
		}
		return b;
	}
	
	public static String getStartEndChar(String word)
	{
		return String.valueOf(word.charAt(0))+String.valueOf(word.charAt(word.length()-1));
	}
}
