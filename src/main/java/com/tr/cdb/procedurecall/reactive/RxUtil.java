package com.tr.cdb.procedurecall.reactive;
import rx.Observable;
import rx.Observer;
import rx.schedulers.Schedulers;
import java.util.ArrayList;

import static rx.Observable.empty;

public class RxUtil {

	public static void main(String[] args) throws Exception {
		// TODO Auto-generated method stub
//		MergeVoidProcesses(
//				() -> {try {
//					Thread.sleep(10000);
//				} catch (InterruptedException e) {
//					// TODO Auto-generated catch block
//					e.printStackTrace();
//				};
//				System.out.println(Thread.currentThread().getName()+":1");},
//				() -> {System.out.println(Thread.currentThread().getName()+":2");},
//				() -> {System.out.println(Thread.currentThread().getName()+":3");},
//				() -> {System.out.println(Thread.currentThread().getName()+":4");},
//				() -> {System.out.println(Thread.currentThread().getName()+":5");},
//				() -> {try {
//					Thread.sleep(5000);
//				} catch (InterruptedException e) {
//					// TODO Auto-generated catch block
//					e.printStackTrace();
//				};System.out.println(Thread.currentThread().getName()+":6");}
//				).subscribe();
		
		ConcatVoidProcess(
				() -> {try {
					Thread.sleep(10000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				};
				
				System.out.println(Thread.currentThread().getName()+":1");},
				() -> {throw new RuntimeException();
				//System.out.println(Thread.currentThread().getName()+":2");
				},
				() -> {System.out.println(Thread.currentThread().getName()+":3");},
				() -> {System.out.println(Thread.currentThread().getName()+":4");},
				() -> {System.out.println(Thread.currentThread().getName()+":5");},
				() -> {try {
					Thread.sleep(5000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				};System.out.println(Thread.currentThread().getName()+":6");}
				).subscribe(
						new Observer<Object>() {

							@Override
							public void onError(Throwable e) {
								System.out.println("Oh no! Something wrong happened!");
							}
							@Override
							public void onNext(Object item) {
								System.out.println("Item is " + item);
							}

							@Override
							public void onCompleted() {
							System.out.println("Observable completed");
							}
						}
						
						);
		
		Thread.sleep(10000);
	}
	
	public static Observable<Object> MergeVoidProcesses (CDBProcess... args)
	{
		ArrayList<Observable<Object>> processes=new ArrayList<Observable<Object>>();
		
		for (CDBProcess process : args)
		{
			processes.add(process.DeferedParallelProcess());
		}
		
		Observable<Object> MergedResult=Observable.merge(processes);
		
		return MergedResult;
	}
	
	public static Observable<Object> ConcatVoidProcess (CDBProcess... args)
	{
		ArrayList<Observable<Object>> processes=new ArrayList<Observable<Object>>();
		
		for (CDBProcess process : args)
		{
			processes.add(process.DeferedsequentialProcess());
		}
		
		Observable<Object> concatenated=Observable.concat(processes);
		
		return concatenated;		
	}	
	
	@FunctionalInterface
	public interface CDBProcess{
		public void process();
		public default Observable<Object> DeferedParallelProcess()
		{
			return Observable.defer(() -> { 
				process();
				return empty();}).subscribeOn(Schedulers.io());
		}
		
		public default Observable<Object> DeferedsequentialProcess()
		{
			return Observable.defer(() -> { 
				process();
				return empty();});
		}
	};
	
}
