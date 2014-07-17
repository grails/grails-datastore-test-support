package relationship

class Painting {
    String title
    static belongsTo = [painter: Painter, gallery: Gallery]
}