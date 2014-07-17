package relationship

class Gallery {
    String name
    static hasMany = [paintings: Painting]
}