package org.genepattern.gpge.ui.tasks;

import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.text.DateFormat;
import java.util.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Vector;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;
import javax.swing.table.*;
import javax.swing.tree.*;
import javax.swing.tree.DefaultMutableTreeNode;
import org.genepattern.data.pipeline.JobSubmission;
import org.genepattern.data.pipeline.PipelineModel;
import org.genepattern.gpge.ui.tasks.*;
import org.genepattern.util.GPConstants;
import org.genepattern.webservice.*;
import org.genepattern.gpge.ui.treetable.*;

/**
 * Job model
 * 
 * @author Joshua Gould
 */
public class JobModel extends AbstractSortableTreeTableModel {
	String[] columnNames = { "Name", "Completed" };

	Class[] columnClasses = {
			org.jdesktop.swing.treetable.TreeTableModel.class, String.class };

	static JobModel instance = new JobModel();

	RootNode root = new RootNode();
   
   private Comparator comparator = new TaskNameComparator(false);
   private int sortColumn = 0;
   
	private JobModel() {
	}
   
  
   public static String getJobResultFileName(ServerFileNode node) {
       return getJobResultFileName(((JobNode)node.getParent()).job, node.index);
   }
   
   private static int getJobCreationJobNumber(ServerFileNode node) {
       return getJobCreationJobNumber(((JobNode)node.getParent()).job, node.index);
   }
    
   public static String getJobResultFileName(AnalysisJob job, int parameterInfoIndex) {
       String fileName = job.getJobInfo().getParameterInfoArray()[parameterInfoIndex].getValue();
      int index1 = fileName.lastIndexOf('/');
      int index2 = fileName.lastIndexOf('\\');
      int index = (index1 > index2 ? index1 : index2);
      if (index != -1) {
         fileName = fileName.substring(index + 1, fileName
								.length());
         
      }
      return fileName;
    }
    
   private static int getJobCreationJobNumber(AnalysisJob job, int parameterInfoIndex) {
      int jobNumber = job.getJobInfo().getJobNumber();
      String fileName = job.getJobInfo().getParameterInfoArray()[parameterInfoIndex].getValue();
      int index1 = fileName.lastIndexOf('/');
      int index2 = fileName.lastIndexOf('\\');
      int index = (index1 > index2 ? index1 : index2);
      if (index != -1) {
        jobNumber = Integer.parseInt(fileName.substring(0, index));
         
      }
      return jobNumber;
    }

   public static void downloadJobResultFile(AnalysisJob job, int parameterInfoIndex, File destination) throws IOException {
      String jobNumber = String.valueOf(job.getJobInfo().getJobNumber());
     
      String fileName = job.getJobInfo().getParameterInfoArray()[parameterInfoIndex].getValue();
      int index1 = fileName.lastIndexOf('/');
      int index2 = fileName.lastIndexOf('\\');
      int index = (index1 > index2 ? index1 : index2);
      if (index != -1) {
         jobNumber = fileName.substring(0, index);
         fileName = fileName.substring(index + 1, fileName
								.length());
         
      }					           
		URL url = null;	
      try {
			   url = new URL(job.getServer() + "/gp/retrieveResults.jsp?job="
						+ jobNumber + "&e=&filename="
						+ URLEncoder.encode(fileName, "UTF-8"));
			} catch (MalformedURLException x) {
				throw new Error(x);
			} catch (java.io.UnsupportedEncodingException uee) {
				throw new Error("Unable to encode " + fileName);
			}
         
			FileOutputStream fos = null;
			InputStream is = null;
			try {
				URLConnection connection = url.openConnection();
            if(connection instanceof HttpURLConnection) {
               HttpURLConnection httpConn = (HttpURLConnection) connection;
               if(httpConn.getResponseCode()==HttpURLConnection.HTTP_GONE) {
                  throw new FileNotFoundException(fileName + " has been deleted");  
               }
            }
				is = connection.getInputStream();
				byte[] b = new byte[100000];
				int bytesRead = 0;
				fos = new FileOutputStream(destination);
				while ((bytesRead = is.read(b)) != -1) {
					fos.write(b, 0, bytesRead);
				}
			} finally {
				if (is != null) {
					try {
						is.close();
					} catch (IOException x) {
					}
				}
				if (fos != null) {
					try {
						fos.close();
					} catch (IOException x) {
					}
				}
			}
   }
   
	public void addJobListener(JobListener l) {
		listenerList.add(JobListener.class, l);
	}

	public void removeJobListener(JobListener l) {
		listenerList.remove(JobListener.class, l);
	}

	/**
	 * Removes the given file from the model
	 * 
	 * @param serverFile
	 *            Description of the Parameter
	 */
	public void remove(ServerFileNode serverFile) {
		JobNode node = (JobNode) serverFile.getParent();
		int serverFileIndex = node.getIndex(serverFile);
		node.remove(serverFileIndex);
		nodesWereRemoved(node, new int[] { serverFileIndex },
				new Object[] { serverFile });

	}

	/**
	 * Deletes the given file from the server
	 * 
	 * @param serverFile
	 *            Description of the Parameter
	 */
	public void delete(ServerFileNode serverFile) throws WebServiceException {
		JobNode node = (JobNode) serverFile.getParent();
	
      JobInfo jobInfo = node.job.getJobInfo();
      AnalysisWebServiceProxy proxy = new AnalysisWebServiceProxy(
            node.job.getServer(), jobInfo.getUserId());
              
      proxy.deleteJobResultFile(jobInfo.getJobNumber(), jobInfo.getParameterInfoArray()[serverFile.index].getValue());
      
      int serverFileIndex = node.getIndex(serverFile);
      node.remove(serverFileIndex);
      nodesWereRemoved(node, new int[] { serverFileIndex },
            new Object[] { serverFile });
		
	}

	/**
	 * Deletes the all the output files for a job from the server
	 * 
	 * @param node
	 *            Description of the Parameter
	 */
	public void delete(JobNode node) throws WebServiceException {
      JobInfo jobInfo = node.job.getJobInfo();
      AnalysisWebServiceProxy proxy = new AnalysisWebServiceProxy(
            node.job.getServer(), jobInfo.getUserId());
      proxy.deleteJob(jobInfo.getJobNumber());
      int index = root.getIndex(node);
      root.remove(index);
      nodesWereRemoved(root, new int[] { index }, new Object[] { node });
	}

   
   /**
   * Invoked when job is initially submiited
   */
	public void add(AnalysisJob job) {
      if(!isSameServerAndUsername(job)) {
         return;  
      }
      if(!job.isClientJob()) {
         JobNode child = new JobNode(job);
         int insertionIndex = 0;
         List children = root.getChildren();
         if (children != null) {
            insertionIndex = Collections.binarySearch(children, child,
                  comparator);   
         }
         if (insertionIndex < 0) {
            insertionIndex = -insertionIndex - 1;
         }
         
         root.insert(child, insertionIndex);
         if(children==null || children.size()<=1) { // fixes bug in tree table
            nodeStructureChanged(root);  
         } else {
            nodesWereInserted(root, new int[] { insertionIndex });
         }
      }
      notifyJobAdded(job);
	}

   private boolean isSameServerAndUsername(AnalysisJob job) {
      return job.getServer().equals(AnalysisServiceManager.getInstance().getServer()) && job.getJobInfo().getUserId().equals(AnalysisServiceManager.getInstance().getUsername());
   }
   
	public void jobCompleted(AnalysisJob job) {
      if(!isSameServerAndUsername(job)) {
         return;  
      }
      
		JobNode jobNode = findJobNode(job);
      if(jobNode==null) {
         add(job);
         jobNode = findJobNode(job);
      }
		int outputFiles = jobNode.getOutputFiles();
		int[] newIndexs = new int[outputFiles];
		for (int i = 0; i < outputFiles; i++) {
			newIndexs[i] = i;
		}
		nodesWereInserted(jobNode, newIndexs);
		notifyJobCompleted(job);
	}

	public void jobStatusChanged(AnalysisJob job) {
      if(!isSameServerAndUsername(job)) {
         return;  
      }
      JobNode jobNode = findJobNode(job);
      if(jobNode==null) {
         add(job);
         jobNode = findJobNode(job);
      }
		nodeChanged(jobNode);
		notifyJobStatusChanged(job);
	}

	public void sortOrderChanged(SortEvent e) {
		int column = e.getColumn();
		boolean ascending = e.isAscending();
		sortColumn = column;
		if (column == 0) {
        comparator = new TaskNameComparator(ascending);
		} else {
         comparator = new TaskDateComparator(ascending);
		}
      List children = root.getChildren();
      if (children != null) {
         Collections.sort(children, comparator);
			 nodeStructureChanged(root);;
		}
	}

	protected void notifyJobAdded(AnalysisJob job) {
		Object[] listeners = listenerList.getListenerList();
		JobEvent e = null;
		// Process the listeners last to first, notifying
		// those that are interested in this event
		for (int i = listeners.length - 2; i >= 0; i -= 2) {
			if (listeners[i] == JobListener.class) {
				// Lazily create the event:
				if (e == null) {
					e = new JobEvent(this, job);
				}

				((JobListener) listeners[i + 1]).jobAdded(e);
			}
		}
	}

	protected void notifyJobStatusChanged(AnalysisJob job) {
		Object[] listeners = listenerList.getListenerList();
		JobEvent e = null;
		// Process the listeners last to first, notifying
		// those that are interested in this event
		for (int i = listeners.length - 2; i >= 0; i -= 2) {
			if (listeners[i] == JobListener.class) {
				// Lazily create the event:
				if (e == null) {
					e = new JobEvent(this, job);
				}

				((JobListener) listeners[i + 1]).jobStatusChanged(e);
			}
		}
	}

	protected void notifyJobCompleted(AnalysisJob job) {
		Object[] listeners = listenerList.getListenerList();
		JobEvent e = null;
		// Process the listeners last to first, notifying
		// those that are interested in this event
		for (int i = listeners.length - 2; i >= 0; i -= 2) {
			if (listeners[i] == JobListener.class) {
				// Lazily create the event:
				if (e == null) {
					e = new JobEvent(this, job);
				}

				((JobListener) listeners[i + 1]).jobCompleted(e);
			}
		}
	}

	private JobNode findJobNode(AnalysisJob job) {
		for (int i = 0, size = root.getChildCount(); i < size; i++) {
			JobNode n = (JobNode) root.getChildAt(i);
			if (n.job == job) {
				return n;
			}
		}
		return null;
	}

	public void getJobsFromServer() throws WebServiceException {
      root.removeAllChildren();
      nodeStructureChanged(root);
      String server = AnalysisServiceManager.getInstance().getServer();
      String username = AnalysisServiceManager.getInstance().getUsername();
      AnalysisWebServiceProxy proxy = new AnalysisWebServiceProxy(server, username);
      JobInfo[] jobs = proxy.getJobs(username, false);
      Vector children = root.getChildren();
      children = new Vector();

      if (jobs != null) {
         for (int i = 0; i < jobs.length; i++) {
            AnalysisJob job = new AnalysisJob(server, jobs[i]);
            JobNode child = new JobNode(job);
            boolean waitUntilCompletion = false;
            if(job.getJobInfo().getStatus().equals(JobStatus.FINISHED) || job.getJobInfo().getStatus().equals(JobStatus.ERROR)) {
               child.getOutputFiles();
            } else {
               waitUntilCompletion = true;
            }

            int insertionIndex = Collections.binarySearch(children, child,
                     comparator);   
            
            if (insertionIndex < 0) {
               insertionIndex = -insertionIndex - 1;
            }
            
            root.insert(child, insertionIndex);
            if(waitUntilCompletion) {
               TaskLauncher.waitUntilCompletionInNewThread(job);  
            }
         }
      }
      nodeStructureChanged(root);	
	}

	public Class getColumnClass(int column) {
		return columnClasses[column];
	}

	public Object getRoot() {
		return root;
	}

	public static JobModel getInstance() {
		return instance;
	}

	public int getColumnCount() {
		return columnNames.length;
	}

	public String getColumnName(int column) {
		return columnNames[column];
	}

	public Object getValueAt(Object node, int column) {
		if (node instanceof ServerFileNode) {
			ServerFileNode f = (ServerFileNode) node;
			switch (column) {
			case 0:
				return f.name;
			default:
				return null;
			}
		} else if (node instanceof JobNode) {
			JobNode j = (JobNode) node;
			switch (column) {
			case 0:
				return j.toString();
			default:
				JobInfo jobInfo = j.job.getJobInfo();
				if (!j.complete) {
              String status = jobInfo.getStatus();
              if(status.equals(JobStatus.NOT_STARTED)) {
                  status = "Pending";  
              }
					return status;
				}

				Date d = jobInfo.getDateCompleted();
				return java.text.DateFormat.getDateTimeInstance(
						java.text.DateFormat.SHORT, java.text.DateFormat.SHORT)
						.format(d);
			}
		}
		return null;
	}

	/**
	 * Description of the Class
	 * 
	 * @author Joshua Gould
	 */
	public static class ServerFileNode extends DefaultMutableTreeNode implements
			Comparable {
      /** The name of this output file */
		public final String name;
      
      /** The string for this node to display. Equals the <tt>name</tt> when the output file was not produced by a child job */
      private final String displayString;

      /** The index in the <tt>ParameterInfo</tt> array of this job result file */
		public final int index;

		public ServerFileNode(String displayString, String name, int index) {
         this.displayString = displayString;
			this.name = name;
			this.index = index;
		}
      
      public String getParameterValue() {
          JobNode parent = (JobNode) getParent();
          return parent.job.getJobInfo().getParameterInfoArray()[index]
            .getValue();
      }

      
      /**
		 * Returns the url to download the file from
		 * 
		 * @return The url to retrieve the file from.
		 */
		public URL getURL() {
         String fileName = getJobResultFileName(this);
			try {
            JobNode parent = (JobNode) getParent();
            AnalysisJob job = parent.job;
            int jobNumber = getJobCreationJobNumber(this);
            
				return new URL(job.getServer() + "/gp/retrieveResults.jsp?job="
						+ jobNumber + "&e=&filename="
						+ URLEncoder.encode(fileName, "UTF-8"));
			} catch (MalformedURLException x) {
				throw new Error(x);
			} catch (java.io.UnsupportedEncodingException uee) {
				throw new Error("Unable to encode " + fileName);
			}
		}
      

		public void download(File destination) throws IOException {
			JobNode parent = (JobNode) getParent();
			AnalysisJob job = parent.job;
         downloadJobResultFile(job, index, destination);
		}

		public String toString() {
			return displayString;
		}

		public int compareTo(Object other) {
			ServerFileNode node = (ServerFileNode) other;
			return this.name.compareTo(node.name);
		}
      
		public boolean getAllowsChildren() {
			return false;
		}

		public boolean isLeaf() {
			return true;
		}

	}

	/**
	 * Description of the Class
	 * 
	 * @author Joshua Gould
	 */
	public static class JobNode extends DefaultMutableTreeNode {

		public final AnalysisJob job;

		boolean complete = false;

		public boolean isComplete() {
			return complete;
		}

		public JobNode(AnalysisJob job) {
			this.job = job;
		}

		public String toString() {
			return job.getTaskName() + " (" + job.getJobInfo().getJobNumber()
					+ ")";
		}

		public int getOutputFiles() {
			complete = true;
			int count = 0;
			ParameterInfo[] jobParameterInfo = job.getJobInfo()
					.getParameterInfoArray();
         int jobNumber = job.getJobInfo().getJobNumber();
			for (int j = 0; j < jobParameterInfo.length; j++) {
				if (jobParameterInfo[j].isOutputFile()) {
               int paramJobNumber = jobNumber;
					String fileName = jobParameterInfo[j].getValue();
					int index1 = fileName.lastIndexOf('/');
					int index2 = fileName.lastIndexOf('\\');
					int index = (index1 > index2 ? index1 : index2);
					if (index != -1) {
                  paramJobNumber = Integer.parseInt(fileName.substring(0, index));
						fileName = fileName.substring(index + 1, fileName
								.length());
                  
					}
               String displayString = fileName;
              // if(paramJobNumber != jobNumber) {
               //   displayString = jobParameterInfo[j].getValue(); will prefix fileName with jobNumber/
               // }
					add(new ServerFileNode(displayString, fileName, j));
					count++;
				}
			}
         if(children!=null) {
            Collections.sort(children);// sort files alphabetically
         }
			return count;
		}

		public boolean getAllowsChildren() {
			return true;
		}

		public boolean isLeaf() {
			return false;
		}

	}

	private static class TaskDateComparator implements Comparator {
		boolean ascending;

		public TaskDateComparator(boolean ascending) {
			this.ascending = ascending;
		}

		public int compare(Object obj1, Object obj2) {
			JobNode node1 = null;
			JobNode node2 = null;
			if (ascending) {
				node1 = (JobNode) obj1;
				node2 = (JobNode) obj2;
			} else {
				node1 = (JobNode) obj2;
				node2 = (JobNode) obj1;
			}

			if (!node1.complete && !node2.complete) {
				return 0;//node1.job.getJobInfo().getDateSubmitted().compareTo(node2.job.getJobInfo().getDateSubmitted());
			}
			if (node1.complete && !node2.complete) {
				return 1;
			}
			if (!node1.complete && node2.complete) {
				return -1;
			}
			return node1.job.getJobInfo().getDateCompleted().compareTo(
					node2.job.getJobInfo().getDateCompleted());
		}

		public boolean equals(Object obj1, Object obj2) {
			JobNode node1 = (JobNode) obj1;
			JobNode node2 = (JobNode) obj2;
			return node1.job.getJobInfo().getDateCompleted().equals(
					node2.job.getJobInfo().getDateCompleted());
		}
	}

	private static class TaskNameComparator implements Comparator {
		boolean ascending;

		public TaskNameComparator(boolean ascending) {
			this.ascending = ascending;
		}

		public int compare(Object obj1, Object obj2) {
         JobNode node1 = (JobNode) obj1;
         JobNode node2 = (JobNode) obj2;
         String job1 = node1.job.getTaskName();
         String job2 = node2.job.getTaskName();
         if(job1.equals(job2)) {
            Integer jobNumber1 = new Integer(node1.job.getJobInfo().getJobNumber());
            Integer jobNumber2 = new Integer(node2.job.getJobInfo().getJobNumber());
            return ascending ? jobNumber1.compareTo(jobNumber2):jobNumber2.compareTo(jobNumber1);  
         }
			return ascending ? job1.compareTo(job2): job2.compareTo(job1);
		}

		public boolean equals(Object obj1, Object obj2) {
			JobNode node1 = (JobNode) obj1;
			JobNode node2 = (JobNode) obj2;
         String job1 = node1.toString();
         String job2 = node2.toString();
         return job1.equals(job2);
		}
	}

	private static class RootNode extends DefaultMutableTreeNode {
		public Vector getChildren() {
			return children;
		}
	}

}