package com.localstudy.app.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class StudyRepository {
    private static volatile StudyRepository instance;

    private final StudyDao dao;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private StudyRepository(Context context) {
        dao = StudyDatabase.get(context).dao();
    }

    public static StudyRepository get(Context context) {
        if (instance == null) {
            synchronized (StudyRepository.class) {
                if (instance == null) {
                    instance = new StudyRepository(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    public StudyDao dao() {
        return dao;
    }

    public void io(Runnable action) {
        io.execute(action);
    }

    public void io(Runnable action, Runnable done) {
        io.execute(() -> {
            try {
                action.run();
            } finally {
                if (done != null) {
                    main.post(done);
                }
            }
        });
    }

    public <T> void io(java.util.concurrent.Callable<T> action, Result<T> callback) {
        io.execute(() -> {
            try {
                T value = action.call();
                main.post(() -> callback.onResult(value, null));
            } catch (Exception error) {
                main.post(() -> callback.onResult(null, error));
            }
        });
    }

    public void deleteSubjectCascade(Subject subject, Runnable done) {
        io(() -> {
            dao.deleteSectionsBySubject(subject.id);
            dao.deleteChaptersBySubject(subject.id);
            dao.detachTodosFromSubject(subject.id);
            dao.detachRepeatsFromSubject(subject.id);
            dao.deleteSubject(subject);
            if (done != null) {
                done.run();
            }
        });
    }

    public interface Result<T> {
        void onResult(T value, Exception error);
    }
}
