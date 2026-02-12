package edu.gatech.cc.cellwatch.data.local.dao
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import edu.gatech.cc.cellwatch.data.local.model.CellEntity

@Dao
interface CellDao {
    @Insert
    suspend fun insertCell(cell: CellEntity)
    
    @Delete fun deleteCell(cell: CellEntity)
}
