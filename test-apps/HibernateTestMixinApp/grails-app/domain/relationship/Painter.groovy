package relationship

class Painter {
    String name
    static hasMany = [paintings: Painting]
}