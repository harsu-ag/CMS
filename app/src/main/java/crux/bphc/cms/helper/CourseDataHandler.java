package crux.bphc.cms.helper;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;

import crux.bphc.cms.app.MyApplication;
import io.realm.Realm;
import io.realm.RealmList;
import io.realm.RealmResults;
import io.realm.Sort;
import crux.bphc.cms.models.course.Content;
import crux.bphc.cms.models.course.Course;
import crux.bphc.cms.models.course.CourseSection;
import crux.bphc.cms.models.course.Module;
import crux.bphc.cms.models.forum.Discussion;

/**
 * Created by Harshit Agarwal on 24-11-2017.
 */

public class CourseDataHandler {

    private final static String TAG = CourseDataHandler.class.getName();

    final UserAccount userAccount;

    public CourseDataHandler(Context context) {
        userAccount = new UserAccount(context);
    }

    public static String getCourseName(int courseId) {
        Realm realm = Realm.getInstance(MyApplication.getRealmConfiguration());
        Course course = realm.where(Course.class).equalTo("id", courseId).findFirst();
        String name = course.getShortName();
        realm.close();
        return name;
    }

    public String getActionBarTitle(int courseId){
        Realm realm = Realm.getInstance(MyApplication.getRealmConfiguration());
        Course course = realm.where(Course.class).equalTo("id", courseId).findFirst();
        String title = course.getCourseName()[0] + " " + course.getCourseName()[2];
        realm.close();
        return title;

    }

    private static <T> T deepCopy(T object, Class<T> type) {
        try {
            Gson gson = new Gson();
            return gson.fromJson(gson.toJson(object, type), type);
        } catch (Exception e) {
            Log.e(TAG, "Error while deep copy of type ", e);
            return null;
        }
    }

    /**
     * Deletes old course data in the Realm Db and inserts the passed courses.
     *
     * @param courseList the list of new courses to be replaced in db.
     * @return the Courses that weren't already present in the db.
     */
    public List<Course> setCourseList(List<Course> courseList) {
        if (!userAccount.isLoggedIn()) {
            return null;
        }
        List<Course> newCourses = new ArrayList<>();
        Realm realm = Realm.getInstance(MyApplication.getRealmConfiguration());

        // Check if course present in db, else add to newCourses
        for (Course course : courseList) {
            if (realm.where(Course.class).equalTo("id", course.getId()).findFirst() == null) {
                newCourses.add(course);
            }
        }

        realm.beginTransaction();
        final RealmResults<Course> results = realm.where(Course.class).findAll();
        results.deleteAllFromRealm(); // delete all existing courses
        realm.copyToRealm(courseList); // add all the passed courses
        realm.commitTransaction();
        realm.close();
        return newCourses;
    }

    /**
     * @return returns course list from database
     */
    public List<Course> getCourseList() {
        Realm realm = Realm.getInstance(MyApplication.getRealmConfiguration());
        List<Course> courses = realm.copyFromRealm(realm.where(Course.class).findAll());
        realm.close();
        return courses;
    }

    /**
     * @param courseId    courseId for which the sectionList data is given
     * @param sectionList sectionList data
     * @return parts of section data structure which has new contents or null if userAccount is not logged in.
     */
    public List<CourseSection> setCourseData(int courseId, @NonNull List<CourseSection> sectionList) {
        if (!userAccount.isLoggedIn()) {
            return null;
        }
        List<CourseSection> newPartsInSections = new ArrayList<>();

        Realm realm = Realm.getInstance(MyApplication.getRealmConfiguration());
        //check if course initially had no data
        if (realm.where(CourseSection.class).equalTo("courseId", courseId).findFirst() == null) {
            sectionList.forEach(s -> s.setCourseId(courseId));
            realm.beginTransaction();
            realm.copyToRealmOrUpdate(sectionList);
            realm.commitTransaction();
            newPartsInSections = sectionList; //returns the whole sectionList as the whole course is new
        } else {        //not a new course, compare parts for new data
            for (CourseSection section : sectionList) {
                if (realm.where(CourseSection.class).equalTo("id", section.getId()).findFirst() == null) {
                    // whole section is new
                    section.setCourseId(courseId);
                    newPartsInSections.add(section);
                } else {
                    CourseSection realmSection =
                            realm.where(CourseSection.class).equalTo("id", section.getId()).findFirst();
                    CourseSection newPartInSection = getNewParts(section, realmSection, realm);
                    if (newPartInSection != null) {
                        newPartsInSections.add(newPartInSection);
                    }
                    section.setCourseId(courseId);
                }
            }

            realm.beginTransaction();
            realm.where(CourseSection.class).equalTo("courseId", courseId).findAll().deleteAllFromRealm();
            realm.copyToRealmOrUpdate(sectionList);
            realm.commitTransaction();
        }
        realm.close();
        return newPartsInSections;

    }

    private CourseSection getNewParts(CourseSection section, CourseSection realmSection, Realm realm) {

        if (realmSection == null) {
            return section;
        }
        RealmList<Module> newModules = new RealmList<>();
        if (section.getModules() == null) {
            return null;
        }
        for (Module module : section.getModules()) {
            Module newModule = getNewParts(module,
                    realm.where(Module.class).equalTo("id", module.getId()).findFirst(), realm);
            if (newModule != null) {
                newModules.add(newModule);
                module.setNewContent(true);
            }

        }
        if (!newModules.isEmpty()) {
            CourseSection newSection = deepCopy(section, CourseSection.class);
            newSection.setModules(newModules);
            return newSection;
        }
        return null;

    }

    private Module getNewParts(Module module, Module realmModule, Realm realm) {
        if (realmModule == null) {  //new module as a whole
            return module;
        }
        if (realmModule.getDescription() != null && !realmModule.getDescription().equals(module.getDescription())) {  //the description of module has changed
            return module;
        }
        module.setNewContent(realmModule.isNewContent());
        //copying newContent variable from local db to cloud retrieved data

        RealmList<Content> newContents = new RealmList<>();
        if (module.getContents() == null) {
            return null;
        }
        for (Content content : module.getContents()) {
            Content realmContent = realm.where(Content.class)
                    .equalTo("fileUrl", content.getFileUrl())
                    .equalTo("fileName", content.getFileName())
                    .findFirst();
            Content newContent = realmContent == null ? content : null;
            if (newContent != null) {
                newContents.add(newContent);
            }
        }
        if (!newContents.isEmpty()) {
            Module newModule = deepCopy(module, Module.class);
            newModule.setContents(newContents);
            return newModule;
        }
        return null;
    }

    public List<Discussion> setForumDiscussions(int forumId, List<Discussion> discussions) {
        if (!userAccount.isLoggedIn()) {
            return null;
        }
        List<Discussion> newDiscussions = new ArrayList<>();
        Realm realm = Realm.getInstance(MyApplication.getRealmConfiguration());

        for (Discussion discussion : discussions) {
            if (realm.where(Discussion.class).equalTo("id", discussion.getId()).findFirst() == null) {
                newDiscussions.add(discussion);
            }
        }
        realm.beginTransaction();
        final RealmResults<Discussion> results = realm.where(Discussion.class).equalTo("forumId", forumId).findAll();
        results.deleteAllFromRealm();
        realm.copyToRealm(discussions);
        realm.commitTransaction();
        realm.close();
        return newDiscussions;

    }

    public void deleteCourse(int courseId) {
        Realm realm = Realm.getInstance(MyApplication.getRealmConfiguration());
        realm.beginTransaction();
        realm.where(Course.class).equalTo("id", courseId).findAll().deleteAllFromRealm();
        realm.where(CourseSection.class).equalTo("courseId", courseId).findAll().deleteAllFromRealm();
        realm.commitTransaction();
        realm.close();
    }

    public List<CourseSection> getCourseData(int courseId) {
        List<CourseSection> courseSections;
        Realm realm = Realm.getInstance(MyApplication.getRealmConfiguration());
        courseSections = realm.copyFromRealm(realm
                .where(CourseSection.class)
                .equalTo("courseId", courseId)
                .findAll()
                .sort("id", Sort.ASCENDING));
        realm.close();
        return courseSections;
    }

    public void markAllAsRead(List<CourseSection> courseSections) {
        for (CourseSection courseSection : courseSections) {
            if (courseSection.getModules() != null) {
                for (Module module : courseSection.getModules()) {
                    markModuleAsReadOrUnread(module, false);
                }
            }
        }
    }

    public void markModuleAsReadOrUnread(Module module, boolean isNewContent) {
        module.setNewContent(isNewContent);
        Realm realm = Realm.getInstance(MyApplication.getRealmConfiguration());
        realm.beginTransaction();
        realm.where(Module.class).equalTo("id", module.getId()).findFirst().setNewContent(isNewContent);
        realm.commitTransaction();
        realm.close();
    }

    public int getUnreadCount(int id) {
        int count = 0;
        List<CourseSection> courseSections = getCourseData(id);
        for (CourseSection courseSection : courseSections) {
            if (courseSection.getModules() != null) {
                for (Module module : courseSection.getModules()) {
                    if (module.isNewContent()) {
                        count++;
                    }
                }
            }
        }
        return count;
    }
}
