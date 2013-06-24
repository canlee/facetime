package com.invindible.facetime.ui;

import java.awt.BorderLayout;
import java.awt.EventQueue;
import java.awt.Image;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import javax.swing.ImageIcon;
import javax.swing.JOptionPane;
import javax.swing.JTabbedPane;
import javax.swing.JInternalFrame;
import javax.swing.JToolBar;
import javax.swing.JScrollPane;
import java.awt.Font;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.JButton;

import com.invindible.facetime.algorithm.LDA;
import com.invindible.facetime.algorithm.feature.Features;
import com.invindible.facetime.algorithm.feature.GetPcaLda;
import com.invindible.facetime.database.Oracle_Connect;
import com.invindible.facetime.database.ProjectDao;
import com.invindible.facetime.database.SignDao;
import com.invindible.facetime.database.UserDao;
import com.invindible.facetime.model.LdaFeatures;
import com.invindible.facetime.model.Project;
import com.invindible.facetime.model.Sign;
import com.invindible.facetime.model.UserDeleteModel;
import com.invindible.facetime.model.Wopt;
import com.invindible.facetime.ui.datechooser.DateChooser;
import com.invindible.facetime.util.image.ImageUtil;
import com.invindible.facetime.wavelet.Wavelet;
import com.sun.org.apache.xpath.internal.operations.Or;

import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import javax.swing.JTable;
import javax.swing.border.TitledBorder;
import javax.swing.event.TableModelListener;
import javax.swing.JScrollBar;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

public class FrameManagerMainUI extends JFrame {

	static FrameManagerMainUI frameManagerMainUI;
	private JPanel contentPane;
	private JTextField txtTime;
	private static Connection conn;
	private JLabel labelPictureSign;
	private JLabel labelPictureUserList;
	private JTextField txtUserName;
	private String searchName;
	private JTable tableSign;
	private JTable tableUserList;
	private Object[][] signObject;
	private Object[][] userListObject;
	private DefaultTableModel tableModelSign;
	private DefaultTableModel tableModelUserList;
	private ImageIcon[] userSignPictures;
	private ImageIcon[] userListPictures;
	private int[] deleteId;
	private JButton btnSearch;
	
	private JButton buttonChooseTime;
	private DateChooser dateChooser;
	private String datetime; 


	/**
	 * Launch the application.
	 */
	public static void main(String[] args) {
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					FrameManagerMainUI frame = new FrameManagerMainUI();
					frame.setVisible(true);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	//删除数据后，对数据库中剩余的样本重新训练
	public static void TrainAfterDelete()
	{
		//设置人数，和每人的照片数（此处默认每人5张）
		int peopleNum = 2;//2是暂定的，需要根据数据库进行修改
		int photoNum = 5;
		
		//WoptT矩阵
		double[][] WoptT;
		//Project数据
		Project pr;
//		//保存从数据库获取的图片的数组
//		BufferedImage[] bImages = null;
		
		//用来PCA、LDA计算的数组
		ImageIcon[] icon = null;// = new ImageIcon[2*5];//[peopleNum*photoNum]
		
		try
		{
			conn = Oracle_Connect.getInstance().getConn();
			
			//1.读取所有样本的图片
			BufferedImage[] bImg = UserDao.doSelectAll(conn);
			
//			for(int i=0; i<bimg.length; i++)
//			{
//				int[] le = ImageUtil.getPixes(bimg[i]);
//
//				System.out.println("长度:" + le.length);
//			}
			
			//------------------------------peopleNum需要从数据库中获取---------------------------------------------
			//获取peopleNum
			peopleNum = bImg.length / 5;
			
			
			//对bImages[]的图片进行小波变换
			BufferedImage[] waveBImages = Wavelet.Wavelet(bImg);
			
			//2.训练（将 本人的5张照片 和 数据库中的所有照片（每人5张） 投影到WoptT上)
			GetPcaLda.getResult(waveBImages);
			
			//3.计算所有人的投影[N][C-1]
			double[][] modelP=new double[peopleNum*photoNum][peopleNum-1];
			for(int i=0;i<peopleNum*photoNum;i++){
				modelP[i]=LDA.getInstance().calZ(waveBImages[i]);//投影
			}
			
//			需要插入数据库的4个数据
//			1.(为了计算<2>所用)WoptT（从单例中获取）
			//double[] WoptT
			WoptT = LdaFeatures.getInstance().getLastProjectionT();
//			2.训练样例的投影（上面的modelP）
			//double[][] modelP
//			3.（投影Z的）N个人的，类内均值（每个人都有一个均值)
			double[][] modelMean=new double[peopleNum][peopleNum-1];
//			4.（投影Z的）总体均值
			double[] allMean=new double[peopleNum-1];
			
			//3.（投影Z的）N个人的，类内均值（每个人都有一个均值)
			//4.（投影Z的）总体均值
			for(int i=0;i<peopleNum;i++){
				for(int k=0;k<peopleNum-1;k++){
					for(int j=0;j<photoNum;j++){
						modelMean[i][k]+=modelP[photoNum*i+j][k];
					}
					allMean[k]+=modelMean[i][k];
					modelMean[i][k]/=photoNum;
				}			
			}
			
			for(int i=0;i<peopleNum-1;i++)
				allMean[i]/=peopleNum*photoNum;
			
			//以下将所有训练完的数据存入数据库中
			
			//封装double[][] Wopt 进 Wopt wopt
			Wopt wopt = new Wopt();
			wopt.setWopt(WoptT);
			
			
			//将Wopt插入数据库中
			ProjectDao.doinsertWopt(conn, wopt);
			
			//将总体均值m插入数据库中
			double[] m = LdaFeatures.getInstance().getAveVector();
			ProjectDao.doinsertmean(conn, m);
			
			//获取所有用户ID
			int[] userIds = UserDao.selectAllIds(conn);
			
			
			//将每个图像的差值图像[像素][n] 转置成 [n][像素]
			double[][] mAveDeviation = LdaFeatures.getInstance().getAveDeviationDouble();
			double[][] mAveDeviationTrans = Features.matrixTrans(mAveDeviation);
			//将转置后的每个图像的差值图像存进数据库中
			ProjectDao.doinsertPeoplemean(conn, mAveDeviationTrans, userIds);
			
			System.out.println("userId如下：");
			for(int i=0; i<userIds.length; i++)
			{
				System.out.println("第 " + i + " 个userId: " + userIds[i]);
			}
			
			
			System.out.println("peopleMean的维数:" + mAveDeviationTrans.length + " " + mAveDeviationTrans[0].length);
			
			
			
			
			//将每类的差值图像 [像素][n/num] 转置成 [n/num][像素]
			double[][] mi = LdaFeatures.getInstance().getAveDeviationEach();
			double[][] miTrans = Features.matrixTrans(mi);
			//将转置后的mi存进数据库中
			ProjectDao.doinsertclassmean(conn, miTrans, userIds);
			
			System.out.println("classMean的维数：" + miTrans.length + " " + miTrans[0].length);
			
			//封装用户Id和投影Z 进 Project
			Project project = new Project();
			project.setId(userIds);
			project.setProject(modelP);
			//插入所有投影
			ProjectDao.doinsertProject(conn, project);
			
			System.out.println("modelP的维数:" + modelP.length + " " + modelP[0].length);
			
			//提示用户，已训练完
			JOptionPane.showMessageDialog(null, "数据库中剩余用户的特征已训练完!", "提示", JOptionPane.INFORMATION_MESSAGE	);
			
		}
		catch(Exception e1)
		{
			e1.printStackTrace();
		}
	}
	
	/**
	 * Create the frame.
	 */
	public FrameManagerMainUI() {
		setTitle("3.管理员管理-主界面");
		dateChooser = new DateChooser(this);
		
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setBounds(100, 100, 711, 526);
		contentPane = new JPanel();
		contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
		setContentPane(contentPane);
		contentPane.setLayout(null);
		
		JTabbedPane tabbedPane = new JTabbedPane(JTabbedPane.TOP);
		tabbedPane.setBounds(0, 0, 664, 439);
		tabbedPane.setFont(new Font("宋体", Font.PLAIN, 16));
		contentPane.add(tabbedPane);
		
		JPanel panelSign = new JPanel();
		tabbedPane.addTab("员工签到情况", null, panelSign, null);
		panelSign.setLayout(null);
		
		JPanel panelTimePick = new JPanel();
		panelTimePick.setBounds(10, 10, 463, 46);
		panelSign.add(panelTimePick);
		panelTimePick.setLayout(null);
		
		JLabel lblNewLabel = new JLabel("请选择一个时间:");
		lblNewLabel.setFont(new Font("宋体", Font.PLAIN, 14));
		lblNewLabel.setBounds(10, 10, 118, 16);
		panelTimePick.add(lblNewLabel);
		
		txtTime = new JTextField();
		txtTime.setBounds(120, 8, 110, 21);
		panelTimePick.add(txtTime);
		txtTime.setColumns(10);
		txtTime.setEditable(false);
		
		JPanel panelTableSign = new JPanel();
		panelTableSign.setBorder(new TitledBorder(UIManager.getBorder("TitledBorder.border"), "\u7B7E\u5230\u8868", TitledBorder.LEADING, TitledBorder.TOP, null, null));
		panelTableSign.setBounds(4, 63, 655, 333);
		panelSign.add(panelTableSign);
		panelTableSign.setLayout(null);
		
		
		tableModelSign = new DefaultTableModel(new Object[][] 
				{
					{null, null},
				},
				new String[] {
					"\u7528\u6237\u540D", "\u7B7E\u5230\u65E5\u671F"
				}
		)
//		{
////			public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
////				// TODO Auto-generated method stub
////				
////			}
//			
//	        public Class getColumnClass(int c) {
//	            return getValueAt(0, c).getClass();
//	        }
//		}
		;
		
		JScrollPane scrollPane = new JScrollPane();
		scrollPane.setBounds(6, 27, 476, 310);
		panelTableSign.add(scrollPane);
		
		tableSign = new JTable();
		tableSign.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				
				int selectedIndex = tableSign.getSelectedRow();
				
				
				try
				{
					if( selectedIndex != -1)
					{
						labelPictureSign.setIcon(userSignPictures[selectedIndex]);
					}
				}
				catch(Exception e1)
				{
					JOptionPane.showMessageDialog(null, "尚无数据，请先选择一个日期！", "提示", JOptionPane.INFORMATION_MESSAGE);
					e1.printStackTrace();
				}
				
			}
		});
		scrollPane.setViewportView(tableSign);
		
		tableSign.setModel(tableModelSign);
//		table.setModel(new DefaultTableModel(
//			new Object[][] {
//				{new Integer(1), null},
//			},
//			new String[] {
//				"\u7528\u6237\u540D", "\u7B7E\u5230\u65E5\u671F"
//			}
//		));
		
		JPanel panel_2 = new JPanel();
		panel_2.setBorder(new TitledBorder(UIManager.getBorder("TitledBorder.border"), "\u7528\u6237\u5934\u50CF", TitledBorder.LEADING, TitledBorder.TOP, null, null));
		panel_2.setBounds(492, 34, 140, 152);
		panelTableSign.add(panel_2);
		panel_2.setLayout(null);
		

		
		labelPictureSign = new JLabel("New label");
		labelPictureSign.setBounds(6, 17, 128, 128);
		panel_2.add(labelPictureSign);
		tableSign.getColumnModel().getColumn(1).setPreferredWidth(150);
		
		
		
		JPanel panelUserList = new JPanel();
		tabbedPane.addTab("用户列表", null, panelUserList, null);
		panelUserList.setLayout(null);
		
		JButton btnNewButton = new JButton("确认");
		btnNewButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				
				String time = txtTime.getText();

				try 
				{
					conn = Oracle_Connect.getInstance().getConn();
					
					//获取签到对象
					ArrayList<Sign> sign = SignDao.doselect(time, conn);
					
					signObject= new Object[sign.size()][3];
					
					userSignPictures = new ImageIcon[sign.size()];
					
					int i;
					for(i=0; i<sign.size(); i++)
					{
						
//						Object[] signOneObject = new Object[3];
						Sign signGet = sign.get(i);
						
						System.out.println("用户名:" + signGet.getUsername());
						System.out.println("日期:" + signGet.getSigndate());
						
						signObject[i][0] = signGet.getUsername();
						signObject[i][1] = signGet.getSigndate();
//						signObject[i][2] = signGet.getBfi();
						
						int[] picPix = ImageUtil.getPixes(signGet.getBfi());
						
						userSignPictures[i] =  
								new ImageIcon(ImageUtil.getImgByPixels(128, 128, picPix));
						
//						TableModel tableModel = table.getModel();
						
//						tableModel.addRow()
						System.out.println( "表行数：" + tableSign.getModel().getRowCount());
						//若行数不够，则添加一行
						if( (i+1) > tableModelSign.getRowCount() )
						{
							tableModelSign.addRow(signObject[i]);
						}
						//若行数够，则直接赋值
						else
						{
							tableModelSign.setValueAt(signObject[i][0], i, 0);
							tableModelSign.setValueAt(signObject[i][1], i, 1);
//						tableModel.setValueAt(signObject[i][2], i, 2);
//						tableModel.setValueAt(new ImageIcon("Pictures\\X.jpg"), i, 2);
						
//						tableModel.setValueAt(signGet.getBfi(), i, 2);
						
//						tableModel.setValueAt()
//						tableModel.getValueAt(i, 2).
						}
					}
					
					//i为人数
					int deleteRowNum = tableModelSign.getRowCount() - i;
//					System.out.println("_------------------------------_");
//					System.out.println("总行数：" + tableModelSign.getRowCount());
//					System.out.println("deleteRowNum:" + deleteRowNum);
					int deleteIndex = tableModelSign.getRowCount() - deleteRowNum;
					//若有多余的行，则删除(每删除一行，后面的行会自动往前补全)
					for(int j=0; j < deleteRowNum; j++)
					{
						tableModelSign.removeRow(deleteIndex);
//						System.out.println("j为:" + j + "删除的行数是：" + deleteIndex);
					}
//					tableModelSign.removeRow(3);//从0开始算起，3为第4个
					
					
//					labelPicture.setIcon(sign.get(0).getBfi())
					
				} catch (Exception e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}

				
			}
		});
		btnNewButton.setFont(new Font("宋体", Font.PLAIN, 14));
		btnNewButton.setBounds(366, 4, 76, 29);
		panelTimePick.add(btnNewButton);
		
		
		buttonChooseTime = new JButton("选择时间");
		buttonChooseTime.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {

                dateChooser.showChooser(buttonChooseTime,  20, 20);
                if(dateChooser.getDate() != null)
                { 
                	datetime=new SimpleDateFormat("yyyy-MM-dd").format(dateChooser.getDate());
                	System.out.println(datetime);
                	txtTime.setText(datetime);
                }
                
                

				
			}
			
			
		});
		buttonChooseTime.setFont(new Font("宋体", Font.PLAIN, 14));
		buttonChooseTime.setBounds(240, 4, 116, 29);
		panelTimePick.add(buttonChooseTime);
		
		//选择时间窗口关闭方法
		 this.addWindowListener(new WindowAdapter(){  //添加窗口关闭事件
	            public void windowClosing(WindowEvent e){
	                
	                setVisible(false);
	                dispose();
	                
	                System.exit(0);
	            }
		 });
		
		
		
		JPanel panelUserSearch = new JPanel();
		panelUserSearch.setLayout(null);
		panelUserSearch.setBounds(10, 10, 347, 46);
		panelUserList.add(panelUserSearch);
		
		JLabel label = new JLabel("请输入用户名：");
		label.setFont(new Font("宋体", Font.PLAIN, 14));
		label.setBounds(10, 10, 118, 16);
		panelUserSearch.add(label);
		
		txtUserName = new JTextField();
		txtUserName.setBounds(116, 8, 110, 21);
		panelUserSearch.add(txtUserName);
		txtUserName.setColumns(10);
		
		btnSearch = new JButton("查询");
		btnSearch.setBounds(236, 7, 93, 23);
		panelUserSearch.add(btnSearch);
		
		JPanel panelTableUserList = new JPanel();
		panelTableUserList.setBorder(new TitledBorder(null, "\u7528\u6237\u5217\u8868", TitledBorder.LEADING, TitledBorder.TOP, null, null));
		panelTableUserList.setLayout(null);
		panelTableUserList.setBounds(4, 63, 655, 333);
		panelUserList.add(panelTableUserList);
		
		JScrollPane scrollPane_1 = new JScrollPane();
		scrollPane_1.setBounds(6, 27, 476, 310);
		panelTableUserList.add(scrollPane_1);
		
		tableUserList = new JTable();
		tableUserList.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				
				int selectedIndex = tableUserList.getSelectedRow();
				
				
				try
				{
					if( selectedIndex != -1)
					{
						//由于酱油的存在，序号+=
//						selectedIndex ++;
						labelPictureUserList.setIcon(userListPictures[selectedIndex]);
					}
				}
				catch(Exception e1)
				{
					JOptionPane.showMessageDialog(null, "尚无数据，请先选择一个日期！", "提示", JOptionPane.INFORMATION_MESSAGE);
					e1.printStackTrace();
				}
				
			}
		});
		tableModelUserList = new DefaultTableModel(new Object[][] {
				{null, null},
			},
			new String[] {
				"\u7528\u6237ID", "\u7528\u6237\u540D"
			}) ;
		tableUserList.setModel(tableModelUserList);
//		tableUserList.setModel(new DefaultTableModel(
//			new Object[][] {
//				{null, null},
//			},
//			new String[] {
//				"\u7528\u6237ID", "\u7528\u6237\u540D"
//			}
//		));
		tableUserList.getColumnModel().getColumn(1).setPreferredWidth(150);
		scrollPane_1.setViewportView(tableUserList);
		
		JPanel panel = new JPanel();
		panel.setBorder(new TitledBorder(UIManager.getBorder("TitledBorder.border"), "\u7528\u6237\u5934\u50CF", TitledBorder.LEADING, TitledBorder.TOP, null, null));
		panel.setBounds(492, 34, 140, 152);
		panelTableUserList.add(panel);
		panel.setLayout(null);
		
		labelPictureUserList = new JLabel("暂无头像");
		labelPictureUserList.setBounds(6, 17, 128, 128);
		panel.add(labelPictureUserList);
		
		JPanel panelDelete = new JPanel();
		panelDelete.setBorder(new TitledBorder(UIManager.getBorder("TitledBorder.border"), "\u5220\u9664\u7528\u6237", TitledBorder.LEADING, TitledBorder.TOP, null, null));
		panelDelete.setBounds(492, 201, 122, 59);
		panelTableUserList.add(panelDelete);
		panelDelete.setLayout(null);
		
		JButton buttonDelete = new JButton("删除选中用户");
		buttonDelete.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				
				int selectedIndex = tableUserList.getSelectedRow();
				
				try
				{
					if( selectedIndex != -1)
					{
						//若确定删除，则从数据库中删除选中用户
						if(JOptionPane.YES_OPTION == 
								JOptionPane.showConfirmDialog(null, "确定删除此用户?此操作不可恢复", "提示", JOptionPane.YES_NO_CANCEL_OPTION))
						{
							Connection conn = Oracle_Connect.getInstance().getConn();
							
							//根据id，删除用户。
							ProjectDao.deleteUserById(conn, deleteId[selectedIndex]);
							
							//删除完毕后,检查数据库还剩多少人
							//若只剩1人（即“酱油”），则把酱油也删了
							if(  UserDao.userRemaining(conn) == 1)
							{
								//从数据库中获取最后的酱油的数据
								ArrayList<UserDeleteModel> arrUserDeleteModel =
										UserDao.selectUser(conn, searchName);
								
								//获取酱油的ID
								int soyId = arrUserDeleteModel.get(0).getId();
								//从数据库中删除酱油
								ProjectDao.deleteUserById(conn, soyId);
								
								//给出提示，数据库中已经无人
								JOptionPane.showMessageDialog(null, "最后一个用户已被删除，数据库中无数据。", "提示", JOptionPane.INFORMATION_MESSAGE);
								
							}
							//若剩下人数不止1个
							//则对数据库中所有样本进行训练，重新保存新数据.
							else
							{
								TrainAfterDelete();
							}
							
						}
						else
						{
							return;
						}
						
						btnSearch.doClick();
					}
					else
					{
						JOptionPane.showMessageDialog(null, "请先选中一个用户!", "提示",  JOptionPane.INFORMATION_MESSAGE);
					}
						
				}
				catch(Exception e1)
				{
					JOptionPane.showMessageDialog(null, "尚无数据,请查找用户,并选中一个用户。", "提示",  JOptionPane.INFORMATION_MESSAGE);

					e1.printStackTrace();
				}
			}

		});
		buttonDelete.setBounds(6, 17, 110, 35);
		panelDelete.add(buttonDelete);
		
		JButton button = new JButton("返回主界面");
		button.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				
				
				if( JOptionPane.YES_OPTION == 
						JOptionPane.showConfirmDialog(null, "确定返回主界面？", "提示", JOptionPane.YES_NO_OPTION))
				{
					frameManagerMainUI.dispose();
					
					MainUI.frameMainUI = new MainUI();
					MainUI.frameMainUI.setVisible(true);
				}
				else
				{
					return;
				}
				
			}
		});
		button.setBounds(295, 449, 110, 35);
		contentPane.add(button);
		btnSearch.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				
				searchName = txtUserName.getText();
				System.out.println("searchName:" + searchName);
				
//				//如果是空的
//				if( searchName.equals(""))
//				{
//					
//				}
//				table.
				
				try 
				{
					conn = Oracle_Connect.getInstance().getConn();
					
					//查找指定用户
					ArrayList<UserDeleteModel> arrUserDeleteModel =
							UserDao.selectUser(conn, searchName);
					
					//若找到的人数为0，则删除所有行
					if(arrUserDeleteModel.size() == 0)
					{
						int rowSize = tableModelUserList.getRowCount();
						System.out.println("目前行数为:" + tableModelUserList.getRowCount());
						for(int i=0; i < rowSize; i++)
						{
							System.out.println("i:" + i);
							tableModelUserList.removeRow(0);
						}
						return;
					}
					
					
					//遍历一遍队列
					for(int i=0; i<arrUserDeleteModel.size(); i++)
					{
						//若有酱油，则将酱油从队列中移除
						if(arrUserDeleteModel.get(i).getUsername().equals("none"))
						{
							arrUserDeleteModel.remove(i);
							//若因酱油删除后，人数为0，则直接跳出函数
							if(arrUserDeleteModel.size() == 0)
							{
								return;
							}
							break;
						}
					}
					
					userListObject = new Object[arrUserDeleteModel.size()][3];
					
					userListPictures = new ImageIcon[arrUserDeleteModel.size()];

					deleteId = new int[arrUserDeleteModel.size()];
					
					
					int i;
					for(i=0; i<arrUserDeleteModel.size(); i++)
					{
//						Object[] userListOneObject = new Object[3];
						UserDeleteModel userDelete = arrUserDeleteModel.get(i);
						
						System.out.println("找到的ID:" + userDelete.getId());
						System.out.println("找到的用户名:" + userDelete.getUsername());
						
						deleteId[i] = userDelete.getId();
						
						userListObject[i][0] = userDelete.getId();
						userListObject[i][1] = userDelete.getUsername();
						userListObject[i][2] = userDelete.getBfi();
						
						int[] picPix = ImageUtil.getPixes(userDelete.getBfi());
						
						userListPictures[i] = 
								new ImageIcon(ImageUtil.getImgByPixels(128, 128, picPix));
						
//						TableModel tableModel = tableUserList.getModel();
						
						//若行数不够，则添加一行
						if( (i+1) > tableModelUserList.getRowCount() )
						{
							tableModelUserList.addRow(userListObject[i]);
						}
						//若行数够，则直接赋值
						else
						{
							tableModelUserList.setValueAt(userListObject[i][0], i, 0);
							tableModelUserList.setValueAt(userListObject[i][1], i, 1);

						}
					}
					
					//i为人数
					int deleteRowNum = tableModelUserList.getRowCount() - i;
					int deleteIndex = tableModelUserList.getRowCount() - deleteRowNum;
					//若有多余的行，则删除(每删除一行，后面的行会自动往前补全)
					for(int j=0; j < deleteRowNum; j++)
					{
						tableModelUserList.removeRow(deleteIndex);
						System.out.println("删除的行数是：" + deleteIndex);
					}
						
//					//由于酱油是多余的，在此删除酱油
//					//相应的，点击响应事件中的selectIndex应该+1
//					tableModelUserList.removeRow(0);
					
						
//					table.setModel(dataModel)
					
					
				} catch (Exception e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
			}
		});
	}
}
