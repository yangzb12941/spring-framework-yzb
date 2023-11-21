/*
 * Copyright 2002-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.remoting.httpinvoker;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.remoting.rmi.RemoteInvocationSerializingExporter;
import org.springframework.remoting.support.RemoteInvocation;
import org.springframework.remoting.support.RemoteInvocationResult;
import org.springframework.web.HttpRequestHandler;
import org.springframework.web.util.NestedServletException;

/**
 * Servlet-API-based HTTP request handler that exports the specified service bean
 * as HTTP invoker service endpoint, accessible via an HTTP invoker proxy.
 *
 * <p>Deserializes remote invocation objects and serializes remote invocation
 * result objects. Uses Java serialization just like RMI, but provides the
 * same ease of setup as Caucho's HTTP-based Hessian protocol.
 *
 * <p><b>HTTP invoker is the recommended protocol for Java-to-Java remoting.</b>
 * It is more powerful and more extensible than Hessian, at the expense of
 * being tied to Java. Nevertheless, it is as easy to set up as Hessian,
 * which is its main advantage compared to RMI.
 *
 * <p><b>WARNING: Be aware of vulnerabilities due to unsafe Java deserialization:
 * Manipulated input streams could lead to unwanted code execution on the server
 * during the deserialization step. As a consequence, do not expose HTTP invoker
 * endpoints to untrusted clients but rather just between your own services.</b>
 * In general, we strongly recommend any other message format (e.g. JSON) instead.
 *
 * Spring 开发小组意识到在RMI服务和基于HTTP 的服务(如Hessian和 Burlap)之间的空白。
 * 一方面，RMI使用Java标准的对象序列化，但很难穿越防火墙;
 * 另一方面，Hessian/Burlap能很好地穿过防火墙工作，但使用自己私有的一套对象序列化机制。
 *
 * 就这样，Spring的HttpInvoker 应运而生。HttpInvoker 是一个新的远程调用模型，作为Spring框架的一部分，
 * 来执行基于HTTP 的远程调用(让防火墙高兴的事 )，并使用Java 的序列化机制(这是让程序员高兴的事)。
 *
 * 我们首先看看 HttpInvoker 的使用示例。HttpInvoker 是基于HTTP 的远程调用，
 * 同时也是使用Spring中提供的web 服务作为基础，所以我们的测试需要首先搭建Web工程。
 *
 * 使用示例：
 * 1、创建对外接口
 * public interface HttpInvokerTestI{
 *     public String getTestPo(String desp);
 * }
 * 2、创建接口实现类
 * public class HttpInvokerTestImpl implements HttpInvokerTestI{
 *   @Override
 *    public String getTestPo(String desp)
 *    {return "getTestPo " + desp;}
 * }
 * 3、创建服务端配置文件 applicationContext-server.xml
 * <bean name=”httpInvokerTest" class="test.HttpinvokerTestImpl"/>
 *
 * 4、WEB-INF 下创建remote-servlet.xml
 * <bean name="/hit" class="org.Springframework.remoting.httpinvoker.HttpInvokerServiceExporter">
 *   <property name="service" ref="httpInvokerTest" />
 *   <property name="serviceInterface" value="test.HttpInvokerTestI"/>
 * </bean>
 *
 * 5、创建测试端配置 client.xml
 * <bean id="remoteService" class="org.Springframework.remoting.httpinvoker,HttpInvokerProxyFactoryBean">
 *   <property name="serviceUrl" value="http://localhost:8080/httpinvokertest/remoting/hitn/>
 *   <property name="serviceInterface" value="test.HttpInvokerTestI"/>
 * </bean>
 *
 * 6、测试类
 * public static void main(String[] args){
 *    ApplicationContext context = new ClassPathXmlApplicationContext ("classpath:client.xml");
 *    HttpInvokerTestI httpInvokerTestI = (HttpInvokerTestI) context.getBean("remoteService");
 *    System.out.println(httpInvokerTestI.getTestPo("dddd"));
 * }
 *
 * 通过层次关系我们看到 HttpInvokerServiceExporter类实现了 InitializingBean 接口以及HttpRequestHandler 接口。
 * 分析RMI服务时我们已经了解到了，当某个bean 继承自InitializingBean接口的时候，Spring 会确保这个 bean 在初始化时调用其
 * afterPropertiesSet 方法，而对于HtpRequestHandler 接口，因为我们在配置中已经将此接口配置成Web 服务，那么当有相应请
 * 求的时候，Spring的 Web 服务就会将程序引导至 HttpRequestHandler 的 handleRequest 方法中首先，
 * 我们从afterPropertiesSet 方法开始分析，看看在 bean 的初始化过程中做了哪些逻辑。
 *
 * @author Juergen Hoeller
 * @since 1.1
 * @see HttpInvokerClientInterceptor
 * @see HttpInvokerProxyFactoryBean
 * @see org.springframework.remoting.rmi.RmiServiceExporter
 * @see org.springframework.remoting.caucho.HessianServiceExporter
 */
public class HttpInvokerServiceExporter extends RemoteInvocationSerializingExporter implements HttpRequestHandler {

	/**
	 * Reads a remote invocation from the request, executes it,
	 * and writes the remote invocation result to the response.
	 *
	 * 当有 Web 请求时，根据配置中的规则会把路径匹配的访问直接引入对应的 HtpRequestHandler 中。
	 * 本例中的 Web 请求与普通的 Web 请求是有些区别的，因为此处的请求包含着HttpInvoker的处理过程。
	 *
	 * 在handlerRequest 函数中，我们很清楚地看到了 HttpInvoker 处理的大致框架。
	 * HttpInvoker服务简单点说就是将请求的方法，也就是 RemoteInvocation 对象，
	 * 从客户端序列化并通过 Web请求出入服务端，服务端在对传过来的序列化对象进行反序列化还原RemoteInvocation实例，
	 * 然后通过实例中的相关信息进行相关方法的调用，并将执行结果再次的返回给客户端。
	 * 从 handleRequest 函数中我们也可以清晰地看到程序执行的框架结构。
	 *
	 * @see #readRemoteInvocation(HttpServletRequest)
	 * @see #invokeAndCreateResult(org.springframework.remoting.support.RemoteInvocation, Object)
	 * @see #writeRemoteInvocationResult(HttpServletRequest, HttpServletResponse, RemoteInvocationResult)
	 */
	@Override
	public void handleRequest(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {

		try {
			// 从 request 中读取列化对象
			RemoteInvocation invocation = readRemoteInvocation(request);
			//执行调用
			RemoteInvocationResult result = invokeAndCreateResult(invocation, getProxy());
			// 将结果的序列化对象写入输出流
			writeRemoteInvocationResult(request, response, result);
		}
		catch (ClassNotFoundException ex) {
			throw new NestedServletException("Class not found during deserialization", ex);
		}
	}

	/**
	 * Read a RemoteInvocation from the given HTTP request.
	 * <p>Delegates to {@link #readRemoteInvocation(HttpServletRequest, InputStream)} with
	 * the {@link HttpServletRequest#getInputStream() servlet request's input stream}.
	 * @param request current HTTP request
	 * @return the RemoteInvocation object
	 * @throws IOException in case of I/O failure
	 * @throws ClassNotFoundException if thrown by deserialization
	 */
	protected RemoteInvocation readRemoteInvocation(HttpServletRequest request)
			throws IOException, ClassNotFoundException {

		return readRemoteInvocation(request, request.getInputStream());
	}

	/**
	 * Deserialize a RemoteInvocation object from the given InputStream.
	 * <p>Gives {@link #decorateInputStream} a chance to decorate the stream
	 * first (for example, for custom encryption or compression). Creates a
	 * {@link org.springframework.remoting.rmi.CodebaseAwareObjectInputStream}
	 * and calls {@link #doReadRemoteInvocation} to actually read the object.
	 * <p>Can be overridden for custom serialization of the invocation.
	 * @param request current HTTP request
	 * @param is the InputStream to read from
	 * @return the RemoteInvocation object
	 * @throws IOException in case of I/O failure
	 * @throws ClassNotFoundException if thrown during deserialization
	 */
	protected RemoteInvocation readRemoteInvocation(HttpServletRequest request, InputStream is)
			throws IOException, ClassNotFoundException {
		// 创建对象输入流
		ObjectInputStream ois = createObjectInputStream(decorateInputStream(request, is));
		try {
			// 从输入流中读取序列化对象
			return doReadRemoteInvocation(ois);
		}
		finally {
			ois.close();
		}
	}

	/**
	 * Return the InputStream to use for reading remote invocations,
	 * potentially decorating the given original InputStream.
	 * <p>The default implementation returns the given stream as-is.
	 * Can be overridden, for example, for custom encryption or compression.
	 * @param request current HTTP request
	 * @param is the original InputStream
	 * @return the potentially decorated InputStream
	 * @throws IOException in case of I/O failure
	 */
	protected InputStream decorateInputStream(HttpServletRequest request, InputStream is) throws IOException {
		return is;
	}

	/**
	 * Write the given RemoteInvocationResult to the given HTTP response.
	 * @param request current HTTP request
	 * @param response current HTTP response
	 * @param result the RemoteInvocationResult object
	 * @throws IOException in case of I/O failure
	 */
	protected void writeRemoteInvocationResult(
			HttpServletRequest request, HttpServletResponse response, RemoteInvocationResult result)
			throws IOException {

		response.setContentType(getContentType());
		writeRemoteInvocationResult(request, response, result, response.getOutputStream());
	}

	/**
	 * Serialize the given RemoteInvocation to the given OutputStream.
	 * <p>The default implementation gives {@link #decorateOutputStream} a chance
	 * to decorate the stream first (for example, for custom encryption or compression).
	 * Creates an {@link java.io.ObjectOutputStream} for the final stream and calls
	 * {@link #doWriteRemoteInvocationResult} to actually write the object.
	 * <p>Can be overridden for custom serialization of the invocation.
	 * @param request current HTTP request
	 * @param response current HTTP response
	 * @param result the RemoteInvocationResult object
	 * @param os the OutputStream to write to
	 * @throws IOException in case of I/O failure
	 * @see #decorateOutputStream
	 * @see #doWriteRemoteInvocationResult
	 */
	protected void writeRemoteInvocationResult(
			HttpServletRequest request, HttpServletResponse response, RemoteInvocationResult result, OutputStream os)
			throws IOException {
		//获取输入流
		ObjectOutputStream oos =
				createObjectOutputStream(new FlushGuardedOutputStream(decorateOutputStream(request, response, os)));
		try {
			//将序列化对象写入输入流
			doWriteRemoteInvocationResult(result, oos);
		}
		finally {
			oos.close();
		}
	}

	/**
	 * Return the OutputStream to use for writing remote invocation results,
	 * potentially decorating the given original OutputStream.
	 * <p>The default implementation returns the given stream as-is.
	 * Can be overridden, for example, for custom encryption or compression.
	 * @param request current HTTP request
	 * @param response current HTTP response
	 * @param os the original OutputStream
	 * @return the potentially decorated OutputStream
	 * @throws IOException in case of I/O failure
	 */
	protected OutputStream decorateOutputStream(
			HttpServletRequest request, HttpServletResponse response, OutputStream os) throws IOException {

		return os;
	}


	/**
	 * Decorate an {@code OutputStream} to guard against {@code flush()} calls,
	 * which are turned into no-ops.
	 * <p>Because {@link ObjectOutputStream#close()} will in fact flush/drain
	 * the underlying stream twice, this {@link FilterOutputStream} will
	 * guard against individual flush calls. Multiple flush calls can lead
	 * to performance issues, since writes aren't gathered as they should be.
	 * @see <a href="https://jira.spring.io/browse/SPR-14040">SPR-14040</a>
	 */
	private static class FlushGuardedOutputStream extends FilterOutputStream {

		public FlushGuardedOutputStream(OutputStream out) {
			super(out);
		}

		@Override
		public void flush() throws IOException {
			// Do nothing on flush
		}
	}

}
