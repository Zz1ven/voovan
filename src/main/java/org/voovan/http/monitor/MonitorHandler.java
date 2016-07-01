package org.voovan.http.monitor;

import org.voovan.http.server.HttpBizHandler;
import org.voovan.http.server.HttpRequest;
import org.voovan.http.server.HttpResponse;
import org.voovan.Global;
import org.voovan.tools.*;
import org.voovan.tools.json.JSONEncode;
import org.voovan.tools.log.Logger;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 监控业务处理类
 *
 * @author helyho
 *
 * Java Framework.
 * WebSite: https://github.com/helyho/Voovan
 * Licence: Apache v2 License
 */
public class MonitorHandler implements HttpBizHandler {

    /**
     * 获取当前 JVM 线程信息描述
     * @return 线程信息信息集合
     */
    public static Map<String,Object> getThreadPoolInfo(){
        Map<String,Object> threadPoolInfo = new HashMap<String,Object>();
        ThreadPoolExecutor threadPoolInstance = Global.getThreadPool();
        threadPoolInfo.put("ActiveCount",threadPoolInstance.getActiveCount());
        threadPoolInfo.put("CorePoolSize",threadPoolInstance.getCorePoolSize());
        threadPoolInfo.put("FinishedTaskCount",threadPoolInstance.getCompletedTaskCount());
        threadPoolInfo.put("TaskCount",threadPoolInstance.getTaskCount());
        threadPoolInfo.put("QueueSize",threadPoolInstance.getQueue().size());
        return threadPoolInfo;
    }

    /**
     * 获取当前 JVM 线程信息描述
     * @return 线程信息信息集合
     */
    public static List<Map<String,Object>> getThreadDetail(){
        ArrayList<Map<String,Object>> threadDetailList = new ArrayList<Map<String,Object>>();
        for(Thread thread : TEnv.getThreads()){
            Map<String,Object> threadDetail = new Hashtable<String,Object>();
            threadDetail.put("Name",thread.getName());
            threadDetail.put("Id",thread.getId());
            threadDetail.put("Priority",thread.getPriority());
            threadDetail.put("ThreadGroup",thread.getThreadGroup().getName());
            threadDetail.put("StackTrace",TEnv.getStackElementsMessage(thread.getStackTrace()));
            threadDetail.put("State",thread.getState().name());
            threadDetailList.add(threadDetail);
        }
        return threadDetailList;
    }

    /**
     * 获取处理器信息
     * @return 处理器信息 Map
     */
    public static Map<String,Object>  getProcessorInfo(){
        Map<String,Object> processInfo = new Hashtable<String,Object>();
        processInfo.put("ProcessorCount",TPerformance.getProcessorCount());
        processInfo.put("SystemLoadAverage",TPerformance.getSystemLoadAverage());
        return processInfo;
    }

    /**
     * 获取当前JVM加载的对象信息(数量,所占内存大小)
     * @param regex 正则表达式
     * @return 系统对象信息的Map
     */
    public static Map<String,TPerformance.ObjectInfo> getSysObjectInfo(String regex) {
        Map<String,TPerformance.ObjectInfo> result;
        try {
            result = TPerformance.getSysObjectInfo(TEnv.getCurrentPID(),regex);
        } catch (IOException e) {
            result = new Hashtable<String,TPerformance.ObjectInfo>();
        }
        return result;

    }

    /**
     * 获取JVM信息
     * @return JVM 信息的 Map
     */
    public static Map<String,Object> getJVMInfo(){
        Map<String, Object> jvmInfo = new Hashtable<String, Object>();
        for(Entry<Object,Object> entry : System.getProperties().entrySet()){
            jvmInfo.put(entry.getKey().toString(),entry.getValue().toString());
        }
        return jvmInfo;
    }

    /**
     * 对象转换成 JSON 字符串
     *      json 中的换行被处理成"\\r\\n"
     * @param obj 待转换的对象
     * @return JSON 字符串
     */
    public static String toJsonWithLF(Object obj){
        String jsonStr = null;
        try {
            jsonStr = JSONEncode.fromObject(obj);
            jsonStr=jsonStr.replace("\\", "\\/");
            jsonStr=jsonStr.replace("\r", "\\r");
            jsonStr=jsonStr.replace("\n", "\\n");
            return jsonStr;
        } catch (ReflectiveOperationException e) {
            Logger.error(e);
        }
        return "";
    }

    /**
     * 从尾部读取日志信息
     * @param type    日志类型
     * @param lineNumber  日志行数
     * @return 日志信息
     * @throws IOException IO 异常
     */
    public static String readLogs(String type ,int lineNumber) throws IOException {
        String fileName;
        if(type.equals("SYSOUT")){
            fileName = "sysout."+ TDateTime.now("yyyyMMdd")+".log";
        }else if(type.equals("ACCESS")){
            fileName = "access.log";
        }else{
            return null;
        }

        String fullPath = TEnv.getSystemPath("logs"+ File.separator+fileName);
        return new String(TFile.loadFileLastLines(new File(fullPath),lineNumber));
    }

    /**
     * 返回请求分析信息
     * @return 请求分析信息集合
     */
    public static List<RequestAnalysis> requestInfo() {
       return (List<RequestAnalysis>) TObject.mapValueToList(HttpMonitorFilter.getRequestInfos());
    }

    @Override
    public void process(HttpRequest request, HttpResponse response) throws Exception {
        String type = request.getParameter("Type");
        String responseStr = "";
        if(type.equals("JVM")){
            responseStr = toJsonWithLF(getJVMInfo());
        }else if(type.equals("CPU")){
            responseStr = toJsonWithLF(getProcessorInfo());
        }else if(type.equals("Memory")){
            responseStr = toJsonWithLF(TPerformance.getMemoryInfo());
        }else if(type.equals("Objects")){
            String filterWord = request.getParameter("Param1");
            responseStr = toJsonWithLF(getSysObjectInfo(filterWord));
        }else if(type.equals("ObjectCount")){
            responseStr = Integer.toString(getSysObjectInfo("").size());
        }else if(type.equals("Threads")){
            responseStr = toJsonWithLF(getThreadDetail());
        }else if(type.equals("ThreadCount")){
            responseStr = Integer.toString(TEnv.getThreads().length);
        }else if(type.equals("ThreadPool")){
            responseStr = toJsonWithLF(getThreadPoolInfo());
        }else if(type.equals("RequestInfo")){
            responseStr = toJsonWithLF(requestInfo());
        }else if(type.equals("Log")){
            String logType = request.getParameter("Param1");
            int lineNumber = Integer.parseInt(request.getParameter("Param2"));
            responseStr = readLogs(logType,lineNumber);
        }
        response.write(responseStr);
    }
}